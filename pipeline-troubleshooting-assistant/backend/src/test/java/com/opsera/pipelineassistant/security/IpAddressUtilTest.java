package com.opsera.pipelineassistant.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpAddressUtilTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void extractClientIp_withXForwardedFor_returnsFirst() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(request.getHeader("X-Real-IP")).thenReturn(null);

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void extractClientIp_withXForwardedForChain_returnsFirstInChain() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1, 172.16.0.5");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void extractClientIp_withXForwardedForWithSpaces_trims() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.1 , 10.0.0.1");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void extractClientIp_withoutXForwardedFor_fallsBackToXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.7");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void extractClientIp_withBlankXForwardedFor_fallsBackToXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.7");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void extractClientIp_withoutProxyHeaders_returnsRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("192.168.1.100");
    }

    @Test
    void extractClientIp_withBlankXRealIp_fallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("192.168.1.100");
    }

    @Test
    void extractClientIp_withIPv6Address_returnsCorrectly() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("2001:db8::1");

        assertThat(IpAddressUtil.extractClientIp(request)).isEqualTo("2001:db8::1");
    }
}
