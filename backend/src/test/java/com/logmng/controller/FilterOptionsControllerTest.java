package com.logmng.controller;

import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import com.logmng.service.DepartmentService;
import com.logmng.service.FilterOptionsService;
import com.logmng.util.StubDataSource;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.logmng.constants.ScreenConstants;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterOptionsControllerTest {

    private MockMvc mockMvc;
    private CapturingFilterOptionsService filterOptionsService;
    private StubAuthServiceForFilterOptions authService;

    @BeforeEach
    void setUp() {
        filterOptionsService = new CapturingFilterOptionsService();
        authService = new StubAuthServiceForFilterOptions(
                "currentUser",
                false,
                List.of("search-history"),
                Map.of("search-history", "all"));
        FilterOptionsController controller = new FilterOptionsController(filterOptionsService, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDepartments_pendingApprovalsScreen_returnsOptions() throws Exception {
        authService = new StubAuthServiceForFilterOptions(
                "currentUser",
                false,
                List.of(ScreenConstants.PENDING_APPROVALS),
                Map.of("pending-approvals", "all"));
        FilterOptionsController controller = new FilterOptionsController(filterOptionsService, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        filterOptionsService.setResponse(List.of("지원"));

        mockMvc.perform(get("/api/filter-options/departments").param("screen", "pending-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("지원"));

        assertThat(filterOptionsService.getLastScreenId()).isEqualTo("pending-approvals");
    }

    @Test
    void getDepartments_searchHistoryScreen_returnsSharedDepartmentOptions() throws Exception {
        filterOptionsService.setResponse(List.of("지원", "개발"));

        mockMvc.perform(get("/api/filter-options/departments").param("screen", "search-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("지원"))
                .andExpect(jsonPath("$.data[1]").value("개발"));

        assertThat(filterOptionsService.getLastScreenId()).isEqualTo("search-history");
        assertThat(filterOptionsService.getLastUsername()).isEqualTo("currentUser");
    }

    @Test
    void getDepartments_forbiddenWhenRequestedScreenIsNotAllowed() throws Exception {
        mockMvc.perform(get("/api/filter-options/departments").param("screen", "statistics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(filterOptionsService.getLastScreenId()).isNull();
    }

    @Test
    void getDepartments_rejectsUnsupportedScreenId() throws Exception {
        mockMvc.perform(get("/api/filter-options/departments").param("screen", "department-approvers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCREEN_ID"));

        assertThat(filterOptionsService.getLastScreenId()).isNull();
    }

    private static final class CapturingFilterOptionsService extends FilterOptionsService {

        private String lastScreenId;
        private String lastUsername;
        private List<String> response = Collections.emptyList();

        private CapturingFilterOptionsService() {
            super(new DepartmentService(new StubDataSource()));
        }

        @Override
        public List<String> getDepartmentOptions(String screenId, LoginResponse userInfo) {
            this.lastScreenId = screenId;
            this.lastUsername = userInfo != null ? userInfo.getUsername() : null;
            return response;
        }

        private void setResponse(List<String> response) {
            this.response = response;
        }

        private String getLastScreenId() {
            return lastScreenId;
        }

        private String getLastUsername() {
            return lastUsername;
        }
    }

    private static final class StubAuthServiceForFilterOptions extends AuthService {

        private final String username;
        private final boolean isSystemAdmin;
        private final List<String> allowedScreenIds;
        private final Map<String, String> screenScopes;

        private StubAuthServiceForFilterOptions(String username,
                                                boolean isSystemAdmin,
                                                List<String> allowedScreenIds,
                                                Map<String, String> screenScopes) {
            super(null, null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
            this.username = username;
            this.isSystemAdmin = isSystemAdmin;
            this.allowedScreenIds = allowedScreenIds;
            this.screenScopes = screenScopes;
        }

        @Override
        public boolean checkAuth(HttpServletRequest request) {
            return true;
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
            LoginResponse response = new LoginResponse();
            response.setUsername(username);
            response.setIsSystemAdmin(isSystemAdmin);
            response.setAllowedScreenIds(allowedScreenIds);
            response.setScreenScopes(screenScopes);
            return response;
        }
    }
}
