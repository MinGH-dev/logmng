package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.FieldMetadataResponse;
import com.logmng.service.FieldMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 로그 타입 컨트롤러
 */
@RestController
@RequestMapping("/api/log-types")
public class LogTypeController {
    
    private static final Logger log = LoggerFactory.getLogger(LogTypeController.class);
    private final FieldMetadataService fieldMetadataService;
    
    public LogTypeController(FieldMetadataService fieldMetadataService) {
        this.fieldMetadataService = fieldMetadataService;
    }
    
    /**
     * 로그 타입 목록 조회
     * GET /api/log-types
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogTypes() {
        log.debug("로그 타입 목록 조회 요청");
        
        List<Map<String, Object>> logTypes = new ArrayList<>();
        
        // pb_feplog 타입
        Map<String, Object> pbFeplog = new HashMap<>();
        pbFeplog.put("id", "pb_feplog");
        pbFeplog.put("name", "PB FEP Log");
        pbFeplog.put("description", "PB FEP 로그 (pb_send, pb_recv)");
        pbFeplog.put("tables", Arrays.asList("pb_send", "pb_recv"));
        logTypes.add(pbFeplog);
        
        // java_fw_imglog 타입
        Map<String, Object> javaFwImglog = new HashMap<>();
        javaFwImglog.put("id", "java_fw_imglog");
        javaFwImglog.put("name", "Java FW Image Log");
        javaFwImglog.put("description", "Java Framework Image 로그 (imagelog)");
        javaFwImglog.put("tables", Arrays.asList("imagelog"));
        logTypes.add(javaFwImglog);
        
        ApiResponse<List<Map<String, Object>>> response = ApiResponse.success(logTypes);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 로그 타입 정보 조회
     * GET /api/log-types/{typeId}
     */
    @GetMapping("/{typeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLogType(@PathVariable String typeId) {
        log.debug("로그 타입 정보 조회: typeId={}", typeId);
        
        Map<String, Object> logType = null;
        
        if ("pb_feplog".equals(typeId)) {
            logType = new HashMap<>();
            logType.put("id", "pb_feplog");
            logType.put("name", "PB FEP Log");
            logType.put("description", "PB FEP 로그 (pb_send, pb_recv)");
            logType.put("tables", Arrays.asList("pb_send", "pb_recv"));
        } else if ("java_fw_imglog".equals(typeId)) {
            logType = new HashMap<>();
            logType.put("id", "java_fw_imglog");
            logType.put("name", "Java FW Image Log");
            logType.put("description", "Java Framework Image 로그 (imagelog)");
            logType.put("tables", Arrays.asList("imagelog"));
        }
        
        if (logType == null) {
            ApiResponse<Map<String, Object>> errorResponse = 
                    ApiResponse.failure("존재하지 않는 로그 타입입니다.", "LOG_TYPE_NOT_FOUND");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(logType);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 로그 타입별 필드 메타데이터 조회
     * GET /api/log-types/{typeId}/fields
     */
    @GetMapping("/{typeId}/fields")
    public ResponseEntity<ApiResponse<List<FieldMetadataResponse>>> getLogTypeFields(@PathVariable String typeId) {
        log.debug("필드 메타데이터 조회: typeId={}", typeId);
        
        if (!"java_fw_imglog".equals(typeId)) {
            ApiResponse<List<FieldMetadataResponse>> errorResponse = 
                    ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        List<FieldMetadataResponse> fields = fieldMetadataService.getFieldMetadata(typeId);
        ApiResponse<List<FieldMetadataResponse>> response = ApiResponse.success(fields);
        return ResponseEntity.ok(response);
    }
}

