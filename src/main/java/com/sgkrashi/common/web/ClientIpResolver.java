package com.sgkrashi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * The key to rate-limit a caller by: their IP address, resolved so that a client cannot choose it.
 *
 * <p>The application sits behind Railway's edge proxy, and the production profile hands
 * {@code X-Forwarded-For} processing to Tomcat's RemoteIpValve ({@code server.forward-headers-strategy:
 * native}). The valve only believes that header when the connecting peer is one of the configured
 * internal proxies, and then walks it from the RIGHT, skipping trusted proxy addresses, so extra
 * addresses a client puts at the front of the header are ignored. After it has run,
 * {@link HttpServletRequest#getRemoteAddr()} is the real client address.
 *
 * <p>If that still leaves an internal address (the edge sent no usable {@code X-Forwarded-For}), every user
 * would otherwise share one key, so the platform's {@code X-Real-IP} header is used as a fallback, and only
 * then. IPv6 addresses are reduced to their /64 prefix, because one subscriber normally owns a whole /64 and
 * could otherwise rotate through addresses to dodge a limit.
 */
@Component
public class ClientIpResolver {

    static final String REAL_IP_HEADER = "X-Real-IP";

    private final boolean realIpFallback;

    public ClientIpResolver(@Value("${app.client-ip.real-ip-fallback:true}") boolean realIpFallback) {
        this.realIpFallback = realIpFallback;
    }

    /** The normalised address to use as a rate-limit key; never null. */
    public String resolve(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        InetAddress parsed = parseLiteral(address);
        if (realIpFallback && (parsed == null || isInternal(parsed))) {
            InetAddress real = parseLiteral(request.getHeader(REAL_IP_HEADER));
            if (real != null && !isInternal(real)) {
                return normalise(real);
            }
        }
        return parsed != null ? normalise(parsed) : String.valueOf(address);
    }

    /** IPv4 as is; IPv6 as its /64 prefix. */
    static String normalise(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < 8; i += 2) {
                prefix.append(String.format("%02x%02x", bytes[i], bytes[i + 1]));
                if (i < 6) {
                    prefix.append(':');
                }
            }
            return prefix.append("::/64").toString();
        }
        return address.getHostAddress();
    }

    /** Loopback, link-local, private (RFC 1918), carrier-grade NAT 100.64/10, IPv6 unique-local fc00::/7, unspecified. */
    static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
        }
        return (b[0] & 0xFE) == 0xFC;
    }

    /**
     * Parses an IP literal without ever touching DNS; returns null for anything that is not a plain IPv4 or
     * IPv6 address (a hostname, a list, junk, an IPv6 zone id).
     */
    static InetAddress parseLiteral(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.length() > 45) {
            return null;
        }
        boolean v6 = text.indexOf(':') >= 0;
        if (v6 ? !text.matches("[0-9a-f:.]+") : !text.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException | SecurityException ex) {
            return null;
        }
    }
}
