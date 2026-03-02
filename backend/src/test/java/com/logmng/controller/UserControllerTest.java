package com.logmng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.StubDecryptApproverServiceForRoleUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController PUT /api/users/{userId} (role update) tests. §7.4
 * Uses StubDecryptApproverServiceForRoleUpdate to avoid Mockito on Java 25+.
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
                        .sessionAttr("role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void updateUserRole_whenSelfDemotion_returns400() throws Exception {
        stubService.setUpdateException(CustomException.badRequest("자기 자신의 권한은 변경할 수 없습니다.", "SELF_DEMOTION_BLOCKED"));

        mockMvc.perform(put("/api/users/admin1")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SELF_DEMOTION_BLOCKED"));
    }

    @Test
    void updateUserRole_whenSystemAdmin_returns400() throws Exception {
        stubService.setUpdateException(CustomException.badRequest("시스템 관리자는 수정할 수 없습니다.", "SYSTEM_ADMIN_IMMUTABLE"));

        mockMvc.perform(put("/api/users/admin1")
                        .sessionAttr("userId", "admin2")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_IMMUTABLE"));
    }

    @Test
    void updateUserRole_whenLastAdmin_returns400() throws Exception {
        stubService.setUpdateException(CustomException.badRequest("마지막 관리자 권한은 변경할 수 없습니다.", "LAST_ADMIN_BLOCKED"));

        mockMvc.perform(put("/api/users/admin1")
                        .sessionAttr("userId", "admin2")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_BLOCKED"));
    }

    @Test
    void updateUserRole_whenUserNotFound_returns404() throws Exception {
        stubService.setUpdateException(CustomException.notFound("해당 사용자를 찾을 수 없습니다: nonexistent", "USER_NOT_FOUND"));

        mockMvc.perform(put("/api/users/nonexistent")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void updateUserRole_whenInvalidRole_returns400() throws Exception {
        stubService.setUpdateException(CustomException.badRequest("role은 ADMIN 또는 USER여야 합니다.", "INVALID_INPUT"));

        mockMvc.perform(put("/api/users/user1")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateUserRole_whenValid_returns200AndUpdatedUser() throws Exception {
        stubService.setUpdateResult(new UserListItemResponse("user1", "ADMIN", "D1", false));

        mockMvc.perform(put("/api/users/user1")
                        .sessionAttr("userId", "admin1")
                        .sessionAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user1"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}
