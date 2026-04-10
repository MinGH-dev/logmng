package com.logmng.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilTest {

    @Test
    void parseValidIpLiteralOrNull_rejectsAllowlistPattern() {
        assertThat(IpUtil.parseValidIpLiteralOrNull("172.23.111.*")).isNull();
        assertThat(IpUtil.parseValidIpLiteralOrNull("192.168.1.*")).isNull();
    }

    @Test
    void parseValidIpLiteralOrNull_acceptsIpv4() {
        assertThat(IpUtil.parseValidIpLiteralOrNull("172.23.111.10")).isEqualTo("172.23.111.10");
        assertThat(IpUtil.parseValidIpLiteralOrNull(" 10.0.0.1 ")).isEqualTo("10.0.0.1");
    }

    @Test
    void parseValidIpLiteralOrNull_ipv6Loopback_normalizedToV4Loopback() {
        assertThat(IpUtil.parseValidIpLiteralOrNull("::1")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("TC-04: getResolvedClientIpForActivityLog equals parseValidIpLiteralOrNull(getClientIP) when primary header is literal")
    void tc04_resolvedMatchesValidatedGetClientIpWhenPrimaryLiteral() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.2");
        request.setRemoteAddr("10.0.0.1");
        IpUtil u = new IpUtil();
        String gc = u.getClientIP(request);
        String validatedGc = IpUtil.parseValidIpLiteralOrNull(gc);
        assertThat(validatedGc).isNotNull();
        assertThat(u.getResolvedClientIpForActivityLog(request)).isEqualTo(validatedGc);
    }

    @Test
    void getResolvedClientIpForActivityLog_skipsInvalidFirstXffHop() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Forwarded-For", "172.23.111.*, 198.51.100.8");
        r.setRemoteAddr("10.0.0.1");
        IpUtil u = new IpUtil();
        assertThat(u.getResolvedClientIpForActivityLog(r)).isEqualTo("198.51.100.8");
    }
}
