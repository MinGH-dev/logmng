package com.logmng.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenFunctionCapability;
import com.logmng.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-BE-01 (interceptor): pending-approvals read without search-history may GET list/detail.
 */
class ScreenAccessInterceptorTest {

    private ScreenAccessInterceptor interceptor;
    private CapturingAuth auth;

    @BeforeEach
    void setUp() {
        auth = new CapturingAuth();
        interceptor = new ScreenAccessInterceptor(auth, new ObjectMapper());
    }

    @Test
    void getSearchHistoryList_allowedWithPendingApprovalsReadOnly() throws Exception {
        LoginResponse user = new LoginResponse();
        user.setUsername("u1");
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.PENDING_APPROVALS));
        user.setScreenFunctions(Map.of(
                ScreenConstants.PENDING_APPROVALS,
                new ScreenFunctionCapability(true, null, false)
        ));
        auth.setUser(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/search-history");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, res, null)).isTrue();
    }

    @Test
    void getSearchHistoryList_deniedWhenPendingReadFalse() throws Exception {
        LoginResponse user = new LoginResponse();
        user.setUsername("u1");
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.PENDING_APPROVALS));
        user.setScreenFunctions(Map.of(
                ScreenConstants.PENDING_APPROVALS,
                new ScreenFunctionCapability(false, null, false)
        ));
        auth.setUser(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/search-history");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, res, null)).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void zeroPermissionUser_deniesProtectedPath() throws Exception {
        LoginResponse user = new LoginResponse();
        user.setUsername("noperm");
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of());
        auth.setUser(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, res, null)).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void getSearchHistoryDetail_numericId_allowedWithPendingApprovals() throws Exception {
        LoginResponse user = new LoginResponse();
        user.setUsername("u1");
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.PENDING_APPROVALS));
        user.setScreenFunctions(Collections.emptyMap());
        auth.setUser(user);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/search-history/42");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, res, null)).isTrue();
    }

    private static final class CapturingAuth extends AuthService {
        private LoginResponse user;

        CapturingAuth() {
            super(null, null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
        }

        void setUser(LoginResponse user) {
            this.user = user;
        }

        @Override
        public LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
            return user;
        }
    }
}
