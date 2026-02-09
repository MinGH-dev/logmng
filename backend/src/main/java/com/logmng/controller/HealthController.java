package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 헬스 체크 컨트롤러
 */
@RestController
@RequestMapping("/api")
public class HealthController {
    
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    
    /**
     * 헬스 체크
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        log.debug("헬스 체크 요청");
        
        HealthResponse healthResponse = new HealthResponse();
        healthResponse.setStatus("OK");
        healthResponse.setTimestamp(LocalDateTime.now());
        healthResponse.setMessage("로그 관리 시스템 API 서버가 정상적으로 실행 중입니다.");
        
        ApiResponse<HealthResponse> response = ApiResponse.success(healthResponse);
        return ResponseEntity.ok(response);
    }
}

