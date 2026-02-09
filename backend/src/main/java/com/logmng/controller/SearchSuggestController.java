package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.service.SearchSuggestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 검색 추천 컨트롤러
 */
@RestController
@RequestMapping("/api/search")
public class SearchSuggestController {
    
    private static final Logger log = LoggerFactory.getLogger(SearchSuggestController.class);
    private final SearchSuggestService suggestService;
    
    public SearchSuggestController(SearchSuggestService suggestService) {
        this.suggestService = suggestService;
    }
    
    /**
     * 검색 추천 API
     * GET /api/search/suggest
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSuggestions(
            @RequestParam String logType,
            @RequestParam String context, // field | operator | value
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) String fieldName) {
        
        log.debug("검색 추천 요청: logType={}, context={}, prefix={}, fieldName={}", 
                logType, context, prefix, fieldName);
        
        if (!"java_fw_imglog".equals(logType)) {
            ApiResponse<List<Map<String, Object>>> errorResponse = 
                    ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        List<Map<String, Object>> suggestions = suggestService.getSuggestions(
                logType, context, prefix, fieldName);
        
        ApiResponse<List<Map<String, Object>>> response = ApiResponse.success(suggestions);
        return ResponseEntity.ok(response);
    }
}





