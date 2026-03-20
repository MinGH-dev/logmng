package com.logmng.controller;

import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.request.SearchHistoryListRequest;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.SearchHistoryService;
import com.logmng.service.StubAppUserResolver;
import com.logmng.service.StubDecryptApproverService;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

class SearchHistoryControllerTest {

    private MockMvc mockMvc;
    private CapturingSearchHistoryService stubService;

    @BeforeEach
    void setUp() {
        stubService = new CapturingSearchHistoryService();
        stubService.setApproveThrowable(null);
        DecryptApproverService decryptApproverService = new StubDecryptApproverService();
        AuthService authService = new NoopAuthService();
        StubAppUserResolver resolver = new StubAppUserResolver(new StubDataSource());
        resolver.map(20260001L, "requester-1");
        SearchHistoryController controller = new SearchHistoryController(
                stubService,
                decryptApproverService,
                authService,
                new StubDataSource(),
                resolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    /** TC-03: GET /api/search-history with valid session and params returns 200 with list or empty */
    @Test
    void list_scopeSelf_ignoresRequesterFiltersAndFixesCurrentUser() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("department", "D01")
                        .param("username", "other name")
                        .param("userId", "20260002")
                        .param("page", "3")
                        .param("pageSize", "10")
                        .param("sortDirection", "asc")
                        .sessionAttr("userId", "currentUser")
                        .sessionAttr("screenScopes", Map.of("search-history", "self")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getActorUserId()).isEqualTo(20260001L);
        assertThat(captured.getUserId()).isEqualTo(20260001L);
        assertThat(captured.getDepartment()).isNull();
        assertThat(captured.getUsername()).isNull();
        assertThat(captured.getAllowedUserIds()).isNull();
        assertThat(captured.getPage()).isEqualTo(3);
        assertThat(captured.getPageSize()).isEqualTo(10);
        assertThat(captured.getSortField()).isEqualTo("requested_at");
        assertThat(captured.getSortDirection()).isEqualTo("asc");
    }

    @Test
    void list_scopeAll_forwardsNormalizedRequesterFiltersAndDefaults() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("department", " D01 ")
                        .param("username", " alice ")
                        .param("userId", "20260001")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getActorUserId()).isEqualTo(20260001L);
        assertThat(captured.getDepartment()).isEqualTo("D01");
        assertThat(captured.getUsername()).isEqualTo("alice");
        assertThat(captured.getUserId()).isEqualTo(20260001L);
        assertThat(captured.getAllowedUserIds()).isNull();
        assertThat(captured.getPage()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSortField()).isEqualTo("requested_at");
        assertThat(captured.getSortDirection()).isEqualTo("desc");
    }

    @Test
    void list_whenServiceThrows_returns200WithEmptyData() throws Exception {
        SearchHistoryService throwingService = new SearchHistoryService(new StubDataSource(), null, new StubDecryptApproverService()) {
            @Override
            public SearchHistoryListResponse list(SearchHistoryListRequest request) {
                throw new RuntimeException("검색 이력 목록 조회 중 오류가 발생했습니다: simulated failure");
            }
        };
        SearchHistoryController controller = new SearchHistoryController(
                throwingService,
                new StubDecryptApproverService(),
                new NoopAuthService(),
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(get("/api/search-history")
                        .param("department", "영업1팀")
                        .param("username", "김철수")
                        .param("userId", "20260002")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data.length()").value(0))
                .andExpect(jsonPath("$.data.pagination.totalCount").value(0));
    }

    @Test
    void list_scopeAll_emptyOrInvalidUserIdParam_returns200AndTreatsUserIdAsNull() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("department", "영업1팀")
                        .param("username", "김철수")
                        .param("userId", "")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isNull();
        assertThat(captured.getDepartment()).isEqualTo("영업1팀");
        assertThat(captured.getUsername()).isEqualTo("김철수");

        mockMvc.perform(get("/api/search-history")
                        .param("userId", "not-a-number")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(stubService.getLastRequest().getUserId()).isNull();
    }

    /** TC-04: When current user resolution throws (e.g. getCurrentUserInfo throws), list returns 401 not 500 */
    @Test
    void list_whenGetCurrentUserInfoThrows_returns401() throws Exception {
        AuthService throwingAuthService = new AuthService(null, null, null, null, null) {
            @Override
            public com.logmng.dto.response.LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
                throw new RuntimeException("simulated auth failure");
            }
        };
        SearchHistoryController controller = new SearchHistoryController(
                stubService,
                new StubDecryptApproverService(),
                throwingAuthService,
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(get("/api/search-history").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    /** TC-05: When getCurrentUserInfo returns null (no/invalid session), list returns 401 */
    @Test
    void list_whenGetCurrentUserInfoReturnsNull_returns401() throws Exception {
        AuthService nullUserAuthService = new AuthService(null, null, null, null, null) {
            @Override
            public com.logmng.dto.response.LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
                return null;
            }

            @Override
            public boolean checkAuth(jakarta.servlet.http.HttpServletRequest request) {
                return false;
            }
        };
        SearchHistoryController controller = new SearchHistoryController(
                stubService,
                new StubDecryptApproverService(),
                nullUserAuthService,
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(get("/api/search-history").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    /** TC-01 (req 20260317): POST with requestReason → 201, response includes id, approvalStatus. */
    @Test
    void create_withRequestReason_returns201() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "logType", "java_fw_imglog",
                "searchParams", Map.of("startDate", "2026-03-01"),
                "requestReason", "요청 사유 입력"));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
        SearchHistoryCreateRequest captured = stubService.getLastCreateRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getRequestReason()).isEqualTo("요청 사유 입력");
    }

    @Test
    void create_withOptionalCountOverrides_returns201WithCounts() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "logType", "java_fw_imglog",
                "searchParams", Map.of(),
                "searchResultTotalCount", 12,
                "decryptionTargetCount", 3));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.searchResultTotalCount").value(12))
                .andExpect(jsonPath("$.data.decryptionTargetCount").value(3));
    }

    @Test
    void create_whenOnlyOneCountOverride_returns400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "logType", "java_fw_imglog",
                "searchParams", Map.of(),
                "searchResultTotalCount", 5));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** requestReason length > 500 → 400. Req 20260317. */
    @Test
    void create_whenRequestReasonOver500_returns400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "logType", "java_fw_imglog",
                "searchParams", Map.of(),
                "requestReason", "a".repeat(501)));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** TC-02 (req 20260318): Missing required field searchParams → 400 (not 500). */
    @Test
    void create_whenSearchParamsMissing_returns400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "logType", "java_fw_imglog",
                "requestReason", "사유"));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** TC-02: Missing required field logType → 400. */
    @Test
    void create_whenLogTypeMissing_returns400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "searchParams", Map.of("startDate", "2026-03-01"),
                "requestReason", "사유"));
        mockMvc.perform(post("/api/search-history")
                        .contentType("application/json")
                        .content(body)
                        .sessionAttr("userId", 20260001L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** TC-02/TC-03/TC-04 (req 20260317): list with requestedAtFrom, requestedAtTo, approvalStatus (repeated), requestReason. */
    @Test
    void list_forwardsRequestedAtFromToApprovalStatusRequestReason() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("requestedAtFrom", "2026-03-01 00:00:00")
                        .param("requestedAtTo", "2026-03-17 23:59:59")
                        .param("approvalStatus", "PENDING")
                        .param("approvalStatus", "APPROVED")
                        .param("requestReason", "검색어")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getRequestedAtFrom()).isEqualTo("2026-03-01 00:00:00");
        assertThat(captured.getRequestedAtTo()).isEqualTo("2026-03-17 23:59:59");
        assertThat(captured.getApprovalStatuses()).containsExactlyInAnyOrder("PENDING", "APPROVED");
        assertThat(captured.getRequestReason()).isEqualTo("검색어");
    }

    /** TC-06/req 20260317: when service throws IllegalArgumentException (e.g. invalid date format), controller returns 400. */
    @Test
    void list_whenServiceThrowsIllegalArgumentException_returns400() throws Exception {
        SearchHistoryService throwingService = new SearchHistoryService(new StubDataSource(), null, new StubDecryptApproverService()) {
            @Override
            public SearchHistoryListResponse list(SearchHistoryListRequest request) {
                throw new IllegalArgumentException("requestedAtFrom and requestedAtTo must be in format yyyy-MM-dd HH:mm:ss");
            }
        };
        SearchHistoryController ctrl = new SearchHistoryController(
                throwingService,
                new StubDecryptApproverService(),
                new NoopAuthService(),
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctrl)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(get("/api/search-history")
                        .param("requestedAtFrom", "2026-03-01")
                        .param("requestedAtTo", "2026-03-17 23:59:59")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.containsString("yyyy-MM-dd HH:mm:ss")))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void list_emptyOrInvalidPageParams_returns200WithDefaults() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("page", "")
                        .param("pageSize", "")
                        .param("department", "영업1팀")
                        .param("username", "김철수")
                        .param("userId", "20260002")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getPage()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getDepartment()).isEqualTo("영업1팀");
        assertThat(captured.getUserId()).isEqualTo(20260002L);

        mockMvc.perform(get("/api/search-history")
                        .param("page", "abc")
                        .param("pageSize", "xyz")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk());

        assertThat(stubService.getLastRequest().getPage()).isEqualTo(1);
        assertThat(stubService.getLastRequest().getPageSize()).isEqualTo(20);
    }

    /** TC-01: user1 (approver) approves user2's PENDING request; user1 allowed → HTTP 200 */
    @Test
    void approve_whenAllowed_returns200() throws Exception {
        SearchHistoryService successOnlyService = new SearchHistoryService(new StubDataSource(), null, new StubDecryptApproverService()) {
            @Override
            public java.util.Map<String, Object> approve(Long id, Long userId) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("id", id);
                result.put("approvalStatus", "APPROVED");
                result.put("approvedBy", "approver");
                result.put("approvedAt", "2026-03-16T12:00:00");
                return result;
            }
        };
        SearchHistoryController ctrl = new SearchHistoryController(
                successOnlyService,
                new StubDecryptApproverService(),
                new NoopAuthService(),
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctrl)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(post("/api/search-history/1/approve")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("isSystemAdmin", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /** TC-02: user1 approves user2's request; user1 not allowed → HTTP 403, not 500 */
    @Test
    void approve_whenServiceThrowsCustomExceptionForbidden_returns403() throws Exception {
        SearchHistoryService forbiddenService = new SearchHistoryService(new StubDataSource(), null, new StubDecryptApproverService()) {
            @Override
            public java.util.Map<String, Object> approve(Long id, Long userId) {
                throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
            }
        };
        SearchHistoryController ctrl = new SearchHistoryController(
                forbiddenService,
                new StubDecryptApproverService(),
                new NoopAuthService(),
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctrl)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(post("/api/search-history/1/approve")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("isSystemAdmin", true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    /** TC-03: Cross-user approve → response is 200 or 4xx, never 500 */
    @Test
    void approve_whenServiceThrowsRuntimeException_returns400Not500() throws Exception {
        SearchHistoryService throwingService = new SearchHistoryService(new StubDataSource(), null, new StubDecryptApproverService()) {
            @Override
            public java.util.Map<String, Object> approve(Long id, Long userId) {
                throw new RuntimeException("simulated failure");
            }
        };
        SearchHistoryController ctrl = new SearchHistoryController(
                throwingService,
                new StubDecryptApproverService(),
                new NoopAuthService(),
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctrl)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(post("/api/search-history/1/approve")
                        .sessionAttr("userId", 20260001L)
                        .sessionAttr("isSystemAdmin", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void approve_whenGetCurrentUserInfoThrows_returns401() throws Exception {
        AuthService throwingAuthService = new AuthService(null, null, null, null, null) {
            @Override
            public com.logmng.dto.response.LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
                throw new RuntimeException("simulated auth failure");
            }
        };
        SearchHistoryController controller = new SearchHistoryController(
                stubService,
                new StubDecryptApproverService(),
                throwingAuthService,
                new StubDataSource(),
                new StubAppUserResolver(new StubDataSource()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(post("/api/search-history/1/approve"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    private static final class CapturingSearchHistoryService extends SearchHistoryService {

        private static final int MAX_REQUEST_REASON_LENGTH = 500;

        private SearchHistoryListRequest lastRequest;
        private SearchHistoryCreateRequest lastCreateRequest;
        private Long lastApproveId;
        private Long lastApproveUserId;
        private Throwable approveThrowable;

        private CapturingSearchHistoryService() {
            super(new StubDataSource(), null, new StubDecryptApproverService());
        }

        @Override
        public Map<String, Object> create(Long userId, SearchHistoryCreateRequest request) {
            this.lastCreateRequest = request;
            Integer st = request.getSearchResultTotalCount();
            Integer dc = request.getDecryptionTargetCount();
            if ((st != null) != (dc != null)) {
                throw new IllegalArgumentException("searchResultTotalCount and decryptionTargetCount must both be provided or both omitted");
            }
            if (request.getRequestReason() != null && request.getRequestReason().length() > MAX_REQUEST_REASON_LENGTH) {
                throw new IllegalArgumentException("requestReason must not exceed " + MAX_REQUEST_REASON_LENGTH + " characters");
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", 1L);
            result.put("requestedAt", "2026-03-17T12:00:00");
            result.put("expiresAt", "2026-03-18T12:00:00");
            result.put("approvalStatus", "PENDING");
            result.put("searchResultTotalCount", request.getSearchResultTotalCount());
            result.put("decryptionTargetCount", request.getDecryptionTargetCount());
            return result;
        }

        @Override
        public SearchHistoryListResponse list(SearchHistoryListRequest request) {
            this.lastRequest = request;
            return new SearchHistoryListResponse(
                    Collections.emptyList(),
                    new UserActivityLogResponse.PaginationInfo(1, 0, 0L));
        }

        @Override
        public Map<String, Object> approve(Long id, Long userId) {
            this.lastApproveId = id;
            this.lastApproveUserId = userId;
            if (approveThrowable != null) {
                if (approveThrowable instanceof RuntimeException) {
                    throw (RuntimeException) approveThrowable;
                }
                throw new RuntimeException(approveThrowable);
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", id);
            result.put("approvalStatus", "APPROVED");
            result.put("approvedBy", "approver");
            result.put("approvedAt", "2026-03-16T12:00:00");
            return result;
        }

        private SearchHistoryListRequest getLastRequest() {
            return lastRequest;
        }

        private SearchHistoryCreateRequest getLastCreateRequest() {
            return lastCreateRequest;
        }

        void setApproveThrowable(Throwable approveThrowable) {
            this.approveThrowable = approveThrowable;
        }
    }

    private static final class NoopAuthService extends AuthService {

        private NoopAuthService() {
            super(null, null, null, null, null);
        }

        @Override
        public com.logmng.dto.response.LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
            com.logmng.dto.response.LoginResponse r = new com.logmng.dto.response.LoginResponse();
            r.setUsername("currentUser");
            r.setUserId(20260001L);
            r.setSelfContext(new com.logmng.dto.response.LoginResponse.SelfContext("D01", "currentUser", 20260001L));
            return r;
        }

        @Override
        public boolean checkAuth(jakarta.servlet.http.HttpServletRequest request) {
            return true;
        }
    }
}
