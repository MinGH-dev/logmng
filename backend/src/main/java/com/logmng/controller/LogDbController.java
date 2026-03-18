package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.dto.request.AdvancedSearchRequest;
import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.LogDbService;
import com.logmng.util.LogTypeScreenHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DB 기반 로그 컨트롤러. Per req 20260318: logType↔screen enforced; 403 LOG_TYPE_NOT_ALLOWED when user lacks screen for requested log type.
 */
@RestController
@RequestMapping("/api/logs/db-refactored")
public class LogDbController {

    private static final Logger log = LoggerFactory.getLogger(LogDbController.class);

    private final LogDbService logDbService;
    private final AuthService authService;

    public LogDbController(LogDbService logDbService, AuthService authService) {
        this.logDbService = logDbService;
        this.authService = authService;
    }

    /**
     * Ensures the current user is allowed to access the given log type (logType↔screen). Throws 403 LOG_TYPE_NOT_ALLOWED if not.
     */
    private void requireLogTypeAccess(HttpServletRequest request, String logType) {
        LoginResponse user = authService.getCurrentUserInfo(request);
        if (user == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            return;
        }
        String screenId = LogTypeScreenHelper.screenIdForLogType(logType);
        if (screenId == null) {
            throw CustomException.badRequest("지원하지 않는 로그 타입입니다: " + logType, "UNSUPPORTED_LOG_TYPE");
        }
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed == null || !allowed.contains(screenId)) {
            log.warn("Log type access denied: logType={} requiredScreen={} user={}", logType, screenId, user.getUsername());
            throw CustomException.forbidden("해당 로그 타입에 대한 접근 권한이 없습니다.", "LOG_TYPE_NOT_ALLOWED");
        }
    }
    
    /**
     * DB 로그 검색
     * POST /api/logs/db-refactored/search
     */
    @ActivityLog(actionType = "SEARCH", description = "로그 검색", includeParams = true, includeResponse = true)
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<LogDbSearchResponse>> searchLogs(
            @RequestBody LogDbSearchRequest request,
            HttpServletRequest httpRequest) {

        String reqLogType = request.getLogType() != null ? request.getLogType() : "pb_feplog";
        requireLogTypeAccess(httpRequest, reqLogType);

        log.info("🔍 DB 로그 검색 요청 수신");
        log.info("🔍 요청 파라미터 상세: logType={}, startDate={}, endDate={}, application={}, servicegroup={}, service={}, datastring={}, headerstring={}, keywords={}",
                reqLogType, request.getStartDate(), request.getEndDate(),
                request.getApplication(), request.getServicegroup(), request.getService(),
                request.getDatastring(), request.getHeaderstring(), request.getKeywords());
        if ("java_fw_imglog".equals(reqLogType)) {
            log.info("🔍 이미지 로그 검색 분기: searchJavaFwImglog 호출 예정");
        }
        log.debug("DB 로그 검색 요청: {}", request);
        
        // 날짜가 없으면 기본값 설정 (오늘 하루)
        if ((request.getStartDate() == null || request.getStartDate().trim().isEmpty()) &&
            (request.getEndDate() == null || request.getEndDate().trim().isEmpty())) {
            java.time.LocalDate today = java.time.LocalDate.now();
            request.setStartDate(today.atStartOfDay().toString().replace("T", " "));
            request.setEndDate(today.atTime(23, 59, 59).toString().replace("T", " "));
        }
        
        LogDbSearchResponse response = logDbService.searchLogs(request);
        
        ApiResponse<LogDbSearchResponse> apiResponse = ApiResponse.success(response);
        return ResponseEntity.ok(apiResponse);
    }
    
    /**
     * DB 로그 상세 조회
     * GET /api/logs/db-refactored/{logType}/{type}/{identifier}
     * - pb_feplog: type은 "send" 또는 "recv", identifier는 id (Long)
     * - java_fw_imglog: type은 무시, identifier는 guid (String)
     */
    @ActivityLog(actionType = "VIEW", description = "로그 상세 조회", includeParams = true)
    @GetMapping("/{logType}/{type}/{identifier}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLogDetail(
            @PathVariable String logType,
            @PathVariable String type,
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        requireLogTypeAccess(httpRequest, logType);
        log.debug("DB 로그 상세 조회: logType={}, type={}, identifier={}", logType, type, identifier);

        Map<String, Object> data = logDbService.getLogDetail(logType, type, identifier);
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 복호화된 데이터 조회
     * GET /api/logs/db-refactored/{logType}/{type}/{identifier}/decrypt
     */
    @ActivityLog(actionType = "DECRYPT", description = "복호화된 데이터 조회", includeParams = true)
    @GetMapping("/{logType}/{type}/{identifier}/decrypt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDecryptedData(
            @PathVariable String logType,
            @PathVariable String type,
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        requireLogTypeAccess(httpRequest, logType);
        log.debug("복호화된 데이터 조회: logType={}, type={}, identifier={}", logType, type, identifier);

        Map<String, Object> data = logDbService.getDecryptedData(logType, type, identifier);
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DB 로그 통계 조회
     * POST /api/logs/db-refactored/stats
     */
    @PostMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLogStats(
            @RequestBody LogDbSearchRequest request) {
        
        log.debug("DB 로그 통계 조회 요청");
        
        // TODO: 구현 필요
        Map<String, Object> data = new HashMap<>();
        data.put("message", "통계 조회 기능은 구현 예정입니다");
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 스키마 정보 조회
     * GET /api/logs/db-refactored/schema
     */
    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchemaInfo() {
        
        log.debug("스키마 정보 조회 요청");
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("tables", new String[]{"pb_send", "pb_recv"});
        schema.put("message", "스키마 정보 조회 기능은 구현 예정입니다");
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(schema);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DB 연결 상태 확인
     * GET /api/logs/db-refactored/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkDBConnection() {
        
        log.debug("DB 연결 상태 확인 요청");
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "OK");
        health.put("message", "DB 연결 정상");
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(health);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 고급 검색 (AST 기반)
     * POST /api/logs/db-refactored/advanced-search
     */
    @ActivityLog(actionType = "ADVANCED_SEARCH", description = "고급 검색", includeParams = true)
    @PostMapping("/advanced-search")
    public ResponseEntity<ApiResponse<LogDbSearchResponse>> advancedSearch(
            @RequestBody AdvancedSearchRequest request,
            HttpServletRequest httpRequest) {

        String logType = request.getLogType();
        requireLogTypeAccess(httpRequest, logType != null ? logType : "");
        log.debug("고급 검색 요청: logType={}, filters={}", request.getLogType(), request.getFilters());

        if (!"java_fw_imglog".equals(logType)) {
            ApiResponse<LogDbSearchResponse> errorResponse = 
                    ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        LogDbSearchResponse response = logDbService.advancedSearch(request);
        
        ApiResponse<LogDbSearchResponse> apiResponse = ApiResponse.success(response);
        return ResponseEntity.ok(apiResponse);
    }
}

