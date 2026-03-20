package com.logmng.controller;

import com.logmng.service.StubAuthServiceForUserController;
import com.logmng.service.StubDecryptApproverServiceForRoleUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController tests. §7.4
 * Uses AuthService.canAccessUserManagementView for access check (bugfix-4).
 */
class UserControllerTest {

    private MockMvc mockMvc;
    private StubDecryptApproverServiceForRoleUpdate stubService;
    private StubAuthServiceForUserController stubAuthService;

    @BeforeEach
    void setUp() {
        stubService = new StubDecryptApproverServiceForRoleUpdate();
        stubAuthService = new StubAuthServiceForUserController();
        UserController controller = new UserController(stubService, stubAuthService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateUserRole_whenNotLoggedIn_returns401() throws Exception {
        stubAuthService.setCheckAuth(false);

        mockMvc.perform(put("/api/users/20260001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateUserRole_whenNonAdmin_returns403() throws Exception {
        stubAuthService.setCheckAuth(true);
        stubAuthService.setCanAccessUserManagementView(false);

        mockMvc.perform(put("/api/users/20260001")
                        .sessionAttr("userId", "user2")
                        .sessionAttr("isSystemAdmin", false)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void updateUserRole_returns410Gone() throws Exception {
        stubAuthService.setCheckAuth(true);
        stubAuthService.setCanAccessUserManagementView(true);

        mockMvc.perform(put("/api/users/20260001")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("isSystemAdmin", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ENDPOINT_REMOVED"));
    }

    @Test
    void listUsers_whenSystemAdmin_returns200() throws Exception {
        stubAuthService.setCheckAuth(true);
        stubAuthService.setCanAccessUserManagementView(true);

        mockMvc.perform(get("/api/users")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("isSystemAdmin", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listUsers_whenNonAdmin_returns403() throws Exception {
        stubAuthService.setCheckAuth(true);
        stubAuthService.setCanAccessUserManagementView(false);

        mockMvc.perform(get("/api/users")
                        .sessionAttr("userId", "user1")
                        .sessionAttr("isSystemAdmin", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
