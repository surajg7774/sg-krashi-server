package com.sgkrashi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * The key to rate-limit a caller by: their IP address.
 *
 * <p>Behind Railway's edge proxy the connection itself comes from the proxy, so the client address has to
 * come from a header. What the edge does with headers was measured on the live service (Stage 3 spoof
 * check): a client-supplied {@code X-Forwarded-For} reaches the application unchanged (the edge does NOT
 * append the real client address to it), so that header can be chosen by the caller and is NOT trusted.
 * Railway documents {@code X-Real-IP} as the client's remote IP, set by the edge, so it is preferred.
 *
 * <p>Order:
 * <ol>
 *   <li>{@code X-Real-IP}, if it is a plain public IP address;</li>
 *   <li>otherwise {@link HttpServletRequest#getRemoteAddr()}, which Tomcat's RemoteIpValve has already
 *       adjusted using {@code X-Forwarded-For} when the connection came from an internal proxy. This is only a
 *       fallback for a request without a usable {@code X-Real-IP}; on its own it can be forged, which is why
 *       the sensitive endpoints also have per-email and global caps.</li>
 * </ol>
 *
 * <p>IPv6 addresses are reduced to their /64 prefix, because one subscriber normally owns a whole /64 and
 * could otherwise rotate through addresses to dodge a limit.
 */
@Component
public class ClientIpResolver {

    static final String REAL_IP_HEADER = "X-Real-IP";

    private final boolean trustRealIpHeader;

    public ClientIpResolver(@Value("${app.client-ip.trust-x-real-ip:true}") boolean trustRealIpHeader) {
        this.trustRealIpHeader = trustRealIpHeader;
    }

    /** The normalised address to use as a rate-limit key; never null. */
    public String resolve(HttpServletRequest request) {
        if (trustRealIpHeader) {
            InetAddress real = parseLiteral(request.getHeader(REAL_IP_HEADER));
            if (real != null && !isInternal(real)) {
                return normalise(real);
            }
        }
        String address = request.getRemoteAddr();
        InetAddress parsed = parseLiteral(address);
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
