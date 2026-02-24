package com.logmng.controller;

import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.service.SearchHistoryService;
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
 * 검색 이력 API (복호화 승인 부가 기능)
 */
@RestController
@RequestMapping("/api/search-history")
public class SearchHistoryController {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryController.class);

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    private static String getUserId(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object userId = session.getAttribute("userId");
        return userId != null ? userId.toString() : null;
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
        SearchHistoryListResponse data = searchHistoryService.list(userId, page, pageSize, sortField, sortDirection);
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
            HttpServletRequest httpRequest) {
        String userId = getUserId(httpRequest);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        try {
            Map<String, Object> data = searchHistoryService.getDetail(userId, id);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage(), "NOT_FOUND"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage(), "FORBIDDEN"));
        }
    }
}
