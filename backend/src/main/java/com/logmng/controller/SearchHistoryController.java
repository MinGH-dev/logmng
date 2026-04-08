package com.logmng.controller;

import com.logmng.constants.ScreenConstants;
import com.logmng.diagnostic.ApprovalFlowDiagnosticLog;
import com.logmng.dto.request.RejectRequest;
import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.request.SearchHistoryListRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.SearchHistoryService;
import com.logmng.util.DepartmentScopeHelper;
import com.logmng.util.ScopeHelper;
import com.logmng.util.SearchHistoryListContextHelper;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 검색 이력 API (복호화 승인 부가 기능). §6.1.5–6.1.7
 */
@RestController
@RequestMapping("/api/search-history")
public class SearchHistoryController {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryController.class);

    private final SearchHistoryService searchHistoryService;
    private final DecryptApproverService decryptApproverService;
    private final AuthService authService;
    private final DataSource dataSource;
    private final AppUserResolver appUserResolver;
    private final boolean diagnosticApprovalFlow;

    public SearchHistoryController(SearchHistoryService searchHistoryService,
                                   DecryptApproverService decryptApproverService,
                                   AuthService authService,
                                   DataSource dataSource,
                                   AppUserResolver appUserResolver,
                                   @Value("${app.diagnostic.approval-flow:false}") boolean diagnosticApprovalFlow) {
        this.searchHistoryService = searchHistoryService;
        this.decryptApproverService = decryptApproverService;
        this.authService = authService;
        this.dataSource = dataSource;
        this.appUserResolver = appUserResolver;
        this.diagnosticApprovalFlow = diagnosticApprovalFlow;
    }

    /** Current user's username (app_user.username). Resolved from session userId via AuthService. */
    private String getCurrentUsername(HttpServletRequest request) {
        if (request == null) return null;
        com.logmng.dto.response.LoginResponse user = authService.getCurrentUserInfo(request);
        return user != null ? user.getUsername() : null;
    }

    /** Current user's numeric app_user.id from auth/session only (req 20260316). Re-entry: derive from username when session has no userId. */
    private Long getCurrentUserId(HttpServletRequest request) {
        if (request == null) return null;
        com.logmng.dto.response.LoginResponse user = authService.getCurrentUserInfo(request);
        if (user != null && user.getUserId() != null) return user.getUserId();
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            Long byUsername = appUserResolver.getIdByUsername(user.getUsername());
            if (byUsername != null) return byUsername;
        }
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            Object sid = session.getAttribute("userId");
            if (sid instanceof Long) return (Long) sid;
            if (sid instanceof Number) return ((Number) sid).longValue();
            if (sid != null && !sid.toString().trim().isEmpty()) {
                try {
                    return Long.parseLong(sid.toString().trim());
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private static boolean isSystemAdmin(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object v = session.getAttribute("isSystemAdmin");
        return Boolean.TRUE.equals(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getScreenScopes(HttpServletRequest request) {
        if (request == null) return null;
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("screenScopes");
        return v instanceof Map ? (Map<String, String>) v : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getAllowedScreenIds(HttpServletRequest request) {
        if (request == null) return null;
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("allowedScreenIds");
        return v instanceof List ? (List<String>) v : null;
    }

    private static String normalizeOptionalParam(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Parse requester userId query param to Long; null/empty/invalid string → null (avoids Spring conversion 5xx). */
    private static Long parseRequesterUserIdParam(String param) {
        if (param == null || param.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(param.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parse page/pageSize query params to int with defaults; avoids MethodArgumentTypeMismatchException → 500. */
    private static int parsePageParam(String param, int defaultVal) {
        if (param == null || param.trim().isEmpty()) return defaultVal;
        try {
            int v = Integer.parseInt(param.trim());
            return v < 1 ? defaultVal : v;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void logApprovalAuthzDecision(HttpServletRequest request,
                                          Long searchHistoryId,
                                          String action,
                                          Long actorUserId,
                                          String actorUsername,
                                          boolean isSystemAdmin,
                                          boolean canApprove,
                                          String decision,
                                          String denialCode) {
        String scope = ScopeHelper.resolveScope(ScreenConstants.PENDING_APPROVALS, isSystemAdmin, getScreenScopes(request));
        String detail = String.format(
                "path=%s method=%s actorUserId=%s actorUsername=%s isSystemAdmin=%s canApprove=%s screenId=%s effectiveScope=%s action=%s decision=%s denialCode=%s",
                request != null ? request.getRequestURI() : "",
                request != null ? request.getMethod() : "",
                actorUserId != null ? actorUserId : "",
                actorUsername != null ? actorUsername : "",
                isSystemAdmin,
                canApprove,
                ScreenConstants.PENDING_APPROVALS,
                scope != null ? scope : "",
                action != null ? action : "",
                decision,
                denialCode != null ? denialCode : "");
        ApprovalFlowDiagnosticLog.debug(diagnosticApprovalFlow, searchHistoryId != null ? searchHistoryId : -1L, "AUTHZ_DECISION", detail);
    }

    /** Requires (isAdmin or isApprover) AND (isAdmin or screenFunctions.approve for search-history/pending-approvals). Per spec §4.4. Req 20260316: isApprover by Long. */
    private void requireApproverOrAdmin(HttpServletRequest request, Long searchHistoryId, String action) {
        Long userId = getCurrentUserId(request);
        String username = getCurrentUsername(request);
        boolean sysAdmin = isSystemAdmin(request);
        boolean isAdmin = decryptApproverService.isAdmin(sysAdmin);
        if (userId == null) {
            logApprovalAuthzDecision(request, searchHistoryId, action, null, username, sysAdmin, false, "DENY", "UNAUTHORIZED");
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        boolean canApprove = isAdmin || authService.hasApproveForSearchHistory(request);
        if (!isAdmin && !decryptApproverService.isApprover(userId)) {
            logApprovalAuthzDecision(request, searchHistoryId, action, userId, username, sysAdmin, canApprove, "DENY", "FUNCTION_NOT_ALLOWED");
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        if (!canApprove) {
            logApprovalAuthzDecision(request, searchHistoryId, action, userId, username, sysAdmin, false, "DENY", "FUNCTION_NOT_ALLOWED");
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        logApprovalAuthzDecision(request, searchHistoryId, action, userId, username, sysAdmin, canApprove, "ALLOW", null);
    }

    /**
     * 검색 이력 저장
     * POST /api/search-history
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody SearchHistoryCreateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            Map<String, Object> data = searchHistoryService.create(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage(), "BAD_REQUEST"));
        }
    }

    /**
     * 승인 대기 목록 (결재자·관리자 전용). GET /api/search-history/pending §6.1.5
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<SearchHistoryListResponse>> listPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest httpRequest) {
        requireApproverOrAdmin(httpRequest, null, "listPending");
        Long approverUserId = getCurrentUserId(httpRequest);
        if (approverUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        boolean isSystemAdmin = isSystemAdmin(httpRequest);
        String scope = ScopeHelper.resolveScope(ScreenConstants.PENDING_APPROVALS, isSystemAdmin, getScreenScopes(httpRequest));
        boolean scopeAll = "all".equals(scope);
        List<Long> allowedUserIds = "team".equals(scope) ? DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, approverUserId) : null;
        SearchHistoryListResponse data = searchHistoryService.listPending(approverUserId, isSystemAdmin, page, pageSize, scopeAll, allowedUserIds);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 검색 이력 목록 조회
     * GET /api/search-history
     * Req 20260316: User resolution or scope/helper failures must not propagate to GlobalExceptionHandler;
     * return 401 when current user cannot be resolved, 200 with empty list when scope/helper/service throws.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchHistoryListResponse>> list(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String username,
            @RequestParam(name = "userId", required = false) String requesterUserIdParam,
            @RequestParam(required = false) String requestedAtFrom,
            @RequestParam(required = false) String requestedAtTo,
            @RequestParam(required = false) List<String> approvalStatus,
            @RequestParam(required = false) String requestReason,
            @RequestParam(name = "page", required = false) String pageParam,
            @RequestParam(name = "pageSize", required = false) String pageSizeParam,
            @RequestParam(defaultValue = "requested_at") String sortField,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String listContext,
            HttpServletRequest httpRequest) {
        Long currentUserId;
        try {
            currentUserId = getCurrentUserId(httpRequest);
        } catch (Throwable t) {
            log.warn("검색 이력 목록: 현재 사용자 확인 중 예외 (401 반환) type={}", t.getClass().getName(), t);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        List<String> allowedScreens = getAllowedScreenIds(httpRequest);
        if (allowedScreens == null) {
            allowedScreens = Collections.emptyList();
        }
        final String effectiveScreenId;
        try {
            effectiveScreenId = SearchHistoryListContextHelper.resolveEffectiveScreenId(listContext, allowedScreens);
        } catch (SearchHistoryListContextHelper.ListContextResolutionException e) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        try {
            int page = parsePageParam(pageParam, 1);
            int pageSize = parsePageParam(pageSizeParam, 20);
            if (pageSize > 100) pageSize = 100;
            Long requesterUserIdNum = parseRequesterUserIdParam(requesterUserIdParam);
            String scope = ScopeHelper.resolveScope(effectiveScreenId, isSystemAdmin(httpRequest), getScreenScopes(httpRequest));
            SearchHistoryListRequest listRequest = new SearchHistoryListRequest();
            listRequest.setActorUserId(currentUserId);
            listRequest.setPage(page);
            listRequest.setPageSize(pageSize);
            listRequest.setSortField(sortField);
            listRequest.setSortDirection(sortDirection);

            if ("self".equals(scope)) {
                listRequest.setUserId(currentUserId);
            } else {
                listRequest.setDepartment(normalizeOptionalParam(department));
                listRequest.setUsername(normalizeOptionalParam(username));
                listRequest.setUserId(requesterUserIdNum);
                if ("team".equals(scope)) {
                    listRequest.setAllowedUserIds(DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, currentUserId));
                }
            }
            listRequest.setRequestedAtFrom(normalizeOptionalParam(requestedAtFrom));
            listRequest.setRequestedAtTo(normalizeOptionalParam(requestedAtTo));
            listRequest.setApprovalStatuses(approvalStatus != null && !approvalStatus.isEmpty() ? approvalStatus : null);
            listRequest.setRequestReason(normalizeOptionalParam(requestReason));

            SearchHistoryListResponse data = searchHistoryService.list(listRequest);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            log.error("검색 이력 목록 조회 실패: department={}, username={}, userId={}, actorUserId={}, exceptionType={}, message={}",
                    department, username, requesterUserIdParam, currentUserId, t.getClass().getName(), t.getMessage(), t);
            SearchHistoryListResponse empty = new SearchHistoryListResponse(
                    Collections.emptyList(),
                    new UserActivityLogResponse.PaginationInfo(1, 1, 0L));
            return ResponseEntity.ok(ApiResponse.success(empty));
        }
    }

    /**
     * 검색 이력 재요청 (만료 건)
     * POST /api/search-history/{id}/re-request
     */
    @PostMapping("/{id}/re-request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reRequest(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            Map<String, Object> data = searchHistoryService.reRequest(userId, id);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage(), "NOT_FOUND"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage(), "FORBIDDEN"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage(), "BAD_REQUEST"));
        }
    }

    /**
     * 검색 이력 상세 (재조회용)
     * GET /api/search-history/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetail(
            @PathVariable Long id,
            @RequestParam(required = false) String listContext,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        List<String> allowedScreens = getAllowedScreenIds(httpRequest);
        if (allowedScreens == null) {
            allowedScreens = Collections.emptyList();
        }
        final String effectiveScreenId;
        try {
            effectiveScreenId = SearchHistoryListContextHelper.resolveEffectiveScreenId(listContext, allowedScreens);
        } catch (SearchHistoryListContextHelper.ListContextResolutionException e) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        try {
            boolean sysAdmin = isSystemAdmin(httpRequest);
            Map<String, String> scopes = getScreenScopes(httpRequest);
            String scope = ScopeHelper.resolveScope(effectiveScreenId, sysAdmin, scopes);
            List<Long> teamPeers = "team".equals(scope)
                    ? DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, userId)
                    : null;
            Map<String, Object> data = searchHistoryService.getDetail(userId, id, effectiveScreenId, sysAdmin, scopes, teamPeers);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage(), "NOT_FOUND"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage(), "FORBIDDEN"));
        }
    }

    /**
     * 검색 이력 승인 (결재자·관리자 전용). POST /api/search-history/{id}/approve §6.1.6
     * Cross-user approval: never return 500; map resolution/service failures to 401 or 4xx (req 20260316-decrypt-approve-cross-user-server-error).
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId;
        try {
            requireApproverOrAdmin(httpRequest, id, "approve");
            userId = getCurrentUserId(httpRequest);
        } catch (CustomException e) {
            throw e;
        } catch (Throwable t) {
            log.warn("승인: 결재자/사용자 확인 중 예외 (401 반환) id={}, type={}", id, t.getClass().getName(), t);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            Map<String, Object> data = searchHistoryService.approve(id, userId);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
        } catch (Throwable t) {
            ApprovalFlowDiagnosticLog.controllerThrowable(diagnosticApprovalFlow, id, userId, t);
            log.error("승인 처리 중 오류: id={}, userId={}, type={}, message={}", id, userId, t.getClass().getName(), t.getMessage(), t);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.failure("승인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "APPROVAL_ERROR"));
        }
    }

    /**
     * 검색 이력 반려 (결재자·관리자 전용). POST /api/search-history/{id}/reject §6.1.7
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRequest body,
            HttpServletRequest httpRequest) {
        requireApproverOrAdmin(httpRequest, id, "reject");
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        String reason = body != null ? body.getRejectionReason() : null;
        try {
            Map<String, Object> data = searchHistoryService.reject(id, userId, reason);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
        }
    }
}
