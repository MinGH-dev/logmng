package com.logmng.controller;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.RejectRequest;
import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.SearchHistoryService;
import com.logmng.util.ScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SearchHistoryController(SearchHistoryService searchHistoryService,
                                   DecryptApproverService decryptApproverService) {
        this.searchHistoryService = searchHistoryService;
        this.decryptApproverService = decryptApproverService;
    }

    private static String getUserId(HttpServletRequest request) {
        if (request == null) return null;
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("userId");
        if (v != null && !v.toString().isBlank()) return v.toString();
        v = session.getAttribute("username");
        return v != null && !v.toString().isBlank() ? v.toString() : null;
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

    private void requireApproverOrAdmin(HttpServletRequest request) {
        String userId = getUserId(request);
        if (userId == null || userId.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!decryptApproverService.isAdmin(isSystemAdmin(request)) && !decryptApproverService.isApprover(userId)) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
    }

    /**
     * 검색 이력 저장
     * POST /api/search-history
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody SearchHistoryCreateRequest request,
            HttpServletRequest httpRequest) {
        String userId = getUserId(httpRequest);
        if (userId == null || userId.isBlank()) {
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
        requireApproverOrAdmin(httpRequest);
        String approverUserId = getUserId(httpRequest);
        boolean isSystemAdmin = isSystemAdmin(httpRequest);
        SearchHistoryListResponse data = searchHistoryService.listPending(approverUserId, isSystemAdmin, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 검색 이력 목록 조회
     * GET /api/search-history
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchHistoryListResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "requested_at") String sortField,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest) {
        String userId = getUserId(httpRequest);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        String scope = ScopeHelper.resolveScope(ScreenConstants.SEARCH_HISTORY, isSystemAdmin(httpRequest), getScreenScopes(httpRequest));
        boolean scopeAll = "all".equals(scope);
        SearchHistoryListResponse data = searchHistoryService.list(userId, page, pageSize, sortField, sortDirection, scopeAll);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 검색 이력 재요청 (만료 건)
     * POST /api/search-history/{id}/re-request
     */
    @PostMapping("/{id}/re-request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reRequest(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String userId = getUserId(httpRequest);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            String scope = ScopeHelper.resolveScope(ScreenConstants.SEARCH_HISTORY, isSystemAdmin(httpRequest), getScreenScopes(httpRequest));
            boolean scopeAll = "all".equals(scope);
            Map<String, Object> data = searchHistoryService.reRequest(userId, id, scopeAll);
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
            HttpServletRequest httpRequest) {
        String userId = getUserId(httpRequest);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            String scope = ScopeHelper.resolveScope(ScreenConstants.SEARCH_HISTORY, isSystemAdmin(httpRequest), getScreenScopes(httpRequest));
            boolean scopeAll = "all".equals(scope);
            Map<String, Object> data = searchHistoryService.getDetail(userId, id, scopeAll);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage(), "NOT_FOUND"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage(), "FORBIDDEN"));
        }
    }

    /**
     * 검색 이력 승인 (결재자·관리자 전용). POST /api/search-history/{id}/approve §6.1.6
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        requireApproverOrAdmin(httpRequest);
        String userId = getUserId(httpRequest);
        try {
            Map<String, Object> data = searchHistoryService.approve(id, userId);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
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
        requireApproverOrAdmin(httpRequest);
        String userId = getUserId(httpRequest);
        String reason = body != null ? body.getRejectionReason() : null;
        try {
            Map<String, Object> data = searchHistoryService.reject(id, userId, reason);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (CustomException e) {
            throw e;
        }
    }
}
