package com.sgkrashi.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(true);

    private static MockHttpServletRequest request(String remoteAddr, String... headers) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (int i = 0; i < headers.length; i += 2) {
            request.addHeader(headers[i], headers[i + 1]);
        }
        return request;
    }

    @Test
    void aPublicAddressIsTheKeyAndAnyXRealIpHeaderIsIgnored() {
        // The connection (after the proxy valve) already is the client: a header must not override it.
        assertEquals("203.0.113.7", resolver.resolve(request("203.0.113.7", "X-Real-IP", "198.51.100.99")));
    }

    @Test
    void whenOnlyTheInternalProxyAddressIsLeftTheRealIpHeaderIsUsed() {
        assertEquals("203.0.113.9",
                resolver.resolve(request("fd12:3456:789a::1", "X-Real-IP", "203.0.113.9")));
    }

    @Test
    void anInternalAddressInTheRealIpHeaderIsNeverAccepted() {
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "X-Real-IP", "192.168.1.1")));
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "X-Real-IP", "100.64.0.9")));
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "X-Real-IP", "127.0.0.1")));
    }

    @Test
    void junkInTheRealIpHeaderIsIgnoredAndNeverResolvedAsAHostname() {
        for (String junk : new String[]{"evil.example.com", "1.2.3.4, 5.6.7.8", "999.1.1.1", "", "   ", "fe80::1%eth0", "not an ip"}) {
            assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "X-Real-IP", junk)), junk);
        }
    }

    @Test
    void theFallbackCanBeSwitchedOff() {
        ClientIpResolver strict = new ClientIpResolver(false);
        assertEquals("10.0.0.5", strict.resolve(request("10.0.0.5", "X-Real-IP", "203.0.113.9")));
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareOneKey() {
        String a = resolver.resolve(request("2001:db8:aaaa:bbbb:1111:2222:3333:4444"));
        String b = resolver.resolve(request("2001:db8:aaaa:bbbb:ffff:eeee:dddd:cccc"));
        String other = resolver.resolve(request("2001:db8:aaaa:cccc:1111:2222:3333:4444"));
        assertEquals(a, b, "rotating the low 64 bits must not give a fresh allowance");
        assertNotEquals(a, other);
        assertEquals("v6:2001:0db8:aaaa:bbbb::/64", a);
    }

    @Test
    void ipv4AddressesAreNotChanged() {
        assertEquals("198.51.100.20", resolver.resolve(request("198.51.100.20")));
    }
}
