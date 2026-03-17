package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.dto.response.ApiResponse;
import com.logmng.service.AuthService;
import com.logmng.service.LogDbService;
import com.logmng.service.SearchHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 복호화 컨트롤러
 */
@RestController
@RequestMapping("/api/logs/decrypt")
public class DecryptController {

    private static final Logger log = LoggerFactory.getLogger(DecryptController.class);
    private final LogDbService logDbService;
    private final SearchHistoryService searchHistoryService;
    private final AuthService authService;

    public DecryptController(LogDbService logDbService, SearchHistoryService searchHistoryService, AuthService authService) {
        this.logDbService = logDbService;
        this.searchHistoryService = searchHistoryService;
        this.authService = authService;
    }

    /**
     * 단일 로우 복호화
     * POST /api/logs/decrypt/{logType}
     * - 로그인 필수(세션 userId). 미로그인 시 401.
     * - 유효한 복호화 승인(APPROVED, 미만료) 검색 이력이 없으면 403 DECRYPTION_NOT_APPROVED.
     */
    @ActivityLog(actionType = "DECRYPT", description = "단일 로우 복호화", includeParams = true)
    @PostMapping("/{logType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decryptRow(
            @PathVariable String logType,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        com.logmng.dto.response.LoginResponse currentUser = authService.getCurrentUserInfo(httpRequest);
        if (currentUser == null || currentUser.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        Long currentUserId = currentUser.getUserId();
        // 복호화 요청 권한: main 화면 + screenFunctions.main.decrypt 필요 (req 20260306)
        if (!authService.hasDecryptForMain(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("복호화 기능 권한이 없습니다. 권한 그룹에서 검색하기 화면의 복호화 권한이 부여되어 있어야 합니다.", "FUNCTION_NOT_ALLOWED"));
        }
        // 복호화는 "현재 검색에 대한 승인"만 허용. searchHistoryId가 해당 사용자(user_id=app_user.id)·승인·미만료인지 검사 (req 20260316).
        Long searchHistoryId = null;
        Object sid = request.get("searchHistoryId");
        if (sid != null) {
            if (sid instanceof Number) searchHistoryId = ((Number) sid).longValue();
            else if (sid instanceof String) try { searchHistoryId = Long.parseLong((String) sid); } catch (NumberFormatException ignored) { }
        }
        if (searchHistoryId == null || !searchHistoryService.isValidApprovalForUser(searchHistoryId, currentUserId)) {
            log.warn("복호화 거부(승인 미충족): searchHistoryId={}, currentUserId={}", searchHistoryId, currentUserId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("복호화 승인 후 이용 가능합니다. 이번 검색에서 '복호화 승인 요청'을 진행해 주세요.", "DECRYPTION_NOT_APPROVED"));
        }

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

        // Snapshot check: only rows in the approved search result may be decrypted (20260224-decryption-snapshot-final-design-en)
        if (!searchHistoryService.isRowInApprovedSnapshot(searchHistoryId, logType, guid)) {
            log.warn("복호화 거부(스냅샷 미포함): searchHistoryId={}, currentUserId={}, logType={}", searchHistoryId, currentUserId, logType);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다.", "ROW_NOT_IN_APPROVED_SNAPSHOT"));
        }

        try {
            Map<String, Object> decryptedData = logDbService.decryptRow(logType, guid, status);
            ApiResponse<Map<String, Object>> response = ApiResponse.success(decryptedData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("복호화 중 오류 발생: searchHistoryId={}, currentUserId={}, guid={}, status={}", searchHistoryId, currentUserId, guid, status, e);
            ApiResponse<Map<String, Object>> errorResponse =
                    ApiResponse.failure("복호화 실패: " + e.getMessage(), "DECRYPTION_FAILED");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

}

