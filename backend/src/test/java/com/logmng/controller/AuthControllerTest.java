package com.logmng.controller;

import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(new StubAuthService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_returnsSelfContextInUserPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"userId\":20260001,\"password\":\"user123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.selfContext.department").value("D01"))
                .andExpect(jsonPath("$.data.user.selfContext.username").value("self-user"))
                .andExpect(jsonPath("$.data.user.selfContext.userId").value(20260001));
    }

    /** TC-01: GET /api/auth/check with valid session returns 200 and authenticated true with user data */
    @Test
    void check_includesSelfContextForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/auth/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.selfContext.department").value("D01"))
                .andExpect(jsonPath("$.data.selfContext.username").value("self-user"))
                .andExpect(jsonPath("$.data.selfContext.userId").value(20260001));
    }

    /** TC-02: GET /api/auth/check with no/invalid session returns 200 and authenticated false */
    @Test
    void check_noSession_returns200WithAuthenticatedFalse() throws Exception {
        AuthController controller = new AuthController(new StubAuthServiceNoSession());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(get("/api/auth/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.message").exists());
    }

    @Test
    void me_returnsSelfContextForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.selfContext.department").value("D01"))
                .andExpect(jsonPath("$.data.user.selfContext.username").value("self-user"))
                .andExpect(jsonPath("$.data.user.selfContext.userId").value(20260001));
    }

    private static final class StubAuthService extends AuthService {

        private StubAuthService() {
            super(null, null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
        }

        @Override
        public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
            return buildUserResponse();
        }

        @Override
        public boolean checkAuth(HttpServletRequest request) {
            return true;
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
            return buildUserResponse();
        }

        private LoginResponse buildUserResponse() {
            LoginResponse response = new LoginResponse();
            response.setUsername("self-user");
            response.setLoginTime(LocalDateTime.of(2026, 3, 13, 12, 0, 0));
            response.setClientIP("127.0.0.1");
            response.setIsSystemAdmin(false);
            response.setSelfContext(new LoginResponse.SelfContext("D01", "self-user", 20260001L));
            return response;
        }
    }

    private static final class StubAuthServiceNoSession extends AuthService {
        private StubAuthServiceNoSession() {
            super(null, null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
        }

        @Override
        public boolean checkAuth(HttpServletRequest request) {
            return false;
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
            return null;
        }
    }
}
