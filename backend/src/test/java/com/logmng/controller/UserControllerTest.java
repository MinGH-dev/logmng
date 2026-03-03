package com.logmng.controller;

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
 * UserController PUT /api/users/{userId} tests. §7.4
 * PUT returns 410 Gone (req 20250303). Uses isSystemAdmin for admin check.
 */
class UserControllerTest {

    private MockMvc mockMvc;
    private StubDecryptApproverServiceForRoleUpdate stubService;

    @BeforeEach
    void setUp() {
        stubService = new StubDecryptApproverServiceForRoleUpdate();
        UserController controller = new UserController(stubService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateUserRole_whenNotLoggedIn_returns401() throws Exception {
        mockMvc.perform(put("/api/users/user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateUserRole_whenNonAdmin_returns403() throws Exception {
        stubService.setAdmin(false);

        mockMvc.perform(put("/api/users/user1")
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
        stubService.setAdmin(true);

        mockMvc.perform(put("/api/users/user1")
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
        stubService.setAdmin(true);

        mockMvc.perform(get("/api/users")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("isSystemAdmin", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listUsers_whenNonAdmin_returns403() throws Exception {
        stubService.setAdmin(false);

        mockMvc.perform(get("/api/users")
                        .sessionAttr("userId", "user1")
                        .sessionAttr("isSystemAdmin", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
