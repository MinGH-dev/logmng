package com.logmng.controller;

import com.logmng.dto.request.ScreenDisplayLabelsPutRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenDisplayLabelItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.ScreenDisplayLabelApi;
import com.logmng.service.StubDecryptApproverService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-01, TC-02, TC-03 and optional GET labelAdmin stripping (via service contract).
 * Uses hand-written stubs (Mockito concrete-class mocks fail on some JVMs).
 */
class ScreenDisplayLabelControllerTest {

    private MockMvc mockMvc;
    private StubAuthScreenLabels authService;
    private StubAppUserResolverScreenLabels appUserResolver;
    private MutableScreenDisplayLabelApi screenDisplayLabelApi;

    @BeforeEach
    void setUp() {
        authService = new StubAuthScreenLabels();
        appUserResolver = new StubAppUserResolverScreenLabels();
        screenDisplayLabelApi = new MutableScreenDisplayLabelApi();
        StubDecryptApproverService decryptApproverService = new StubDecryptApproverService();
        ScreenDisplayLabelController controller = new ScreenDisplayLabelController(
                screenDisplayLabelApi, authService, decryptApproverService, appUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    private static LoginResponse user(long userId, boolean systemAdmin) {
        LoginResponse u = new LoginResponse();
        u.setUserId(userId);
        u.setUsername("tester");
        u.setIsSystemAdmin(systemAdmin);
        return u;
    }

    @Test
    void tc01_getAuthenticated_returns200WithData() throws Exception {
        authService.setCurrent(user(1L, false));
        screenDisplayLabelApi.listReturn = List.of(new ScreenDisplayLabelItemResponse("pb-feplog", "PB FEP", null, "log-search", 0));

        mockMvc.perform(get("/api/screen-display-labels")
                        .sessionAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].screenId").value("pb-feplog"))
                .andExpect(jsonPath("$.data[0].labelUser").value("PB FEP"))
                .andExpect(jsonPath("$.data[0].parentGroupId").value("log-search"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));

        assertThat(screenDisplayLabelApi.lastListSystemAdmin).isFalse();
    }

    @Test
    void get_whenNotLoggedIn_returns401() throws Exception {
        authService.setCurrent(null);

        mockMvc.perform(get("/api/screen-display-labels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tc02_putNonAdmin_returns403() throws Exception {
        authService.setCurrent(user(2L, false));

        mockMvc.perform(put("/api/screen-display-labels")
                        .sessionAttr("userId", 2L)
                        .sessionAttr("isSystemAdmin", false)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void tc03_putAdmin_success() throws Exception {
        authService.setCurrent(user(1L, true));
        appUserResolver.idByUsername = null;
        screenDisplayLabelApi.replaceReturn = List.of(new ScreenDisplayLabelItemResponse("pb-feplog", "L", "a", "history", 3));

        mockMvc.perform(put("/api/screen-display-labels")
                        .sessionAttr("userId", 1L)
                        .sessionAttr("isSystemAdmin", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[{\"screenId\":\"pb-feplog\",\"labelUser\":\"L\",\"labelAdmin\":\"a\",\"parentGroupId\":\"history\",\"sortOrder\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].screenId").value("pb-feplog"))
                .andExpect(jsonPath("$.data[0].parentGroupId").value("history"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(3));

        assertThat(screenDisplayLabelApi.lastReplaceActorId).isEqualTo(1L);
    }

    @Test
    void tc04_putInvalidScreen_returns400() throws Exception {
        authService.setCurrent(user(1L, true));
        screenDisplayLabelApi.replaceError = CustomException.badRequest("bad", "INVALID_SCREEN_ID");

        mockMvc.perform(put("/api/screen-display-labels")
                        .sessionAttr("userId", 1L)
                        .sessionAttr("isSystemAdmin", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[{\"screenId\":\"x\",\"labelUser\":\"y\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCREEN_ID"));
    }

    @Test
    void tc05_putLabelTooLong_returns400() throws Exception {
        authService.setCurrent(user(1L, true));
        screenDisplayLabelApi.replaceError = CustomException.badRequest("long", "INVALID_INPUT");

        String longLabel = "x".repeat(300);
        mockMvc.perform(put("/api/screen-display-labels")
                        .sessionAttr("userId", 1L)
                        .sessionAttr("isSystemAdmin", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[{\"screenId\":\"pb-feplog\",\"labelUser\":\"" + longLabel + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void get_nonAdmin_jsonOmitsLabelAdmin() throws Exception {
        authService.setCurrent(user(1L, false));
        screenDisplayLabelApi.listReturn = List.of(new ScreenDisplayLabelItemResponse("pb-feplog", "U", null));

        mockMvc.perform(get("/api/screen-display-labels").sessionAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].labelAdmin").doesNotExist());
    }

    private static final class StubAuthScreenLabels extends AuthService {
        private LoginResponse current;

        private StubAuthScreenLabels() {
            super(null, null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
        }

        private void setCurrent(LoginResponse current) {
            this.current = current;
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
            return current;
        }
    }

    private static final class StubAppUserResolverScreenLabels extends AppUserResolver {
        private Long idByUsername;

        private StubAppUserResolverScreenLabels() {
            super(null);
        }

        @Override
        public Long getIdByUsername(String username) {
            return idByUsername;
        }
    }

    /** Mutable stub for per-test behavior (no Mockito). */
    private static final class MutableScreenDisplayLabelApi implements ScreenDisplayLabelApi {

        private List<ScreenDisplayLabelItemResponse> listReturn = List.of();
        private Boolean lastListSystemAdmin;
        private List<ScreenDisplayLabelItemResponse> replaceReturn = List.of();
        private Long lastReplaceActorId;
        private CustomException replaceError;

        @Override
        public List<ScreenDisplayLabelItemResponse> listForViewer(boolean systemAdmin) {
            this.lastListSystemAdmin = systemAdmin;
            return listReturn;
        }

        @Override
        public List<ScreenDisplayLabelItemResponse> replaceAll(ScreenDisplayLabelsPutRequest body, long actorAppUserId) {
            if (replaceError != null) {
                throw replaceError;
            }
            this.lastReplaceActorId = actorAppUserId;
            return replaceReturn;
        }
    }
}
