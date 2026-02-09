package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.dto.response.ApiResponse;
import com.logmng.service.LogDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 복호화 컨트롤러
 */
@RestController
@RequestMapping("/api/logs/decrypt")
public class DecryptController {
    
    private static final Logger log = LoggerFactory.getLogger(DecryptController.class);
    private final LogDbService logDbService;
    
    public DecryptController(LogDbService logDbService) {
        this.logDbService = logDbService;
    }
    
    /**
     * 단일 로우 복호화
     * POST /api/logs/decrypt/{logType}
     */
    @ActivityLog(actionType = "DECRYPT", description = "단일 로우 복호화", includeParams = true)
    @PostMapping("/{logType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decryptRow(
            @PathVariable String logType,
            @RequestBody Map<String, String> request) {
        
        String guid = request.get("guid");
        String status = request.get("status");
        log.info("🔓 복호화 요청: logType={}, guid={}, status={}", logType, guid, status);
        
        if (!"java_fw_imglog".equals(logType)) {
            ApiResponse<Map<String, Object>> errorResponse = 
                    ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        if (guid == null || guid.trim().isEmpty()) {
            ApiResponse<Map<String, Object>> errorResponse = 
                    ApiResponse.failure("GUID는 필수입니다.", "MISSING_GUID");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        try {
            Map<String, Object> decryptedData = logDbService.decryptRow(logType, guid, status);
            ApiResponse<Map<String, Object>> response = ApiResponse.success(decryptedData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("복호화 중 오류 발생: guid={}, status={}", guid, status, e);
            ApiResponse<Map<String, Object>> errorResponse = 
                    ApiResponse.failure("복호화 실패: " + e.getMessage(), "DECRYPTION_FAILED");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

