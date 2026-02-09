package com.logmng.controller;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.service.UserActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 사용자 활동 이력 컨트롤러
 */
@RestController
@RequestMapping("/api/activity-log")
public class UserActivityLogController {
    
    private static final Logger log = LoggerFactory.getLogger(UserActivityLogController.class);
    
    private final UserActivityLogService userActivityLogService;
    
    public UserActivityLogController(UserActivityLogService userActivityLogService) {
        this.userActivityLogService = userActivityLogService;
    }
    
    /**
     * 사용자 활동 이력 검색
     * POST /api/activity-log/search
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<UserActivityLogResponse>> searchActivityLogs(
            @RequestBody UserActivityLogSearchRequest request) {
        
        log.info("🔍 사용자 활동 이력 검색 요청 수신");
        log.debug("🔍 요청 파라미터: {}", request);
        
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);
        
        ApiResponse<UserActivityLogResponse> apiResponse = ApiResponse.success(response);
        return ResponseEntity.ok(apiResponse);
    }
    
    /**
     * 사용자 활동 이력 상세 조회
     * GET /api/activity-log/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActivityLogDetail(
            @PathVariable Long id) {
        
        log.debug("🔍 사용자 활동 이력 상세 조회: id={}", id);
        
        Map<String, Object> data = userActivityLogService.getActivityLogDetail(id);
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }
}





