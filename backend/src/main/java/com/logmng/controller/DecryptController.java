package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.DecryptionRowKey;
import com.logmng.util.LogTypeScreenHelper;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptionAllowedService;
import com.logmng.service.LogDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 복호화 컨트롤러. 권한 판단은 decryption-allowed store만 사용 (req 20260318). searchHistoryId는 감사용 선택.
 */
@RestController
@RequestMapping("/api/logs/decrypt")
public class DecryptController {

    private static final Logger log = LoggerFactory.getLogger(DecryptController.class);
    private final LogDbService logDbService;
    private final DecryptionAllowedService decryptionAllowedService;
    private final AuthService authService;

    public DecryptController(LogDbService logDbService, DecryptionAllowedService decryptionAllowedService, AuthService authService) {
        this.logDbService = logDbService;
        this.decryptionAllowedService = decryptionAllowedService;
        this.authService = authService;
    }

    /**
     * 단일 로우 복호화. Authorization from decryption-allowed store only (req 20260318). searchHistoryId optional for audit.
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

        String screenId = LogTypeScreenHelper.screenIdForLogType(logType);
        if (screenId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE"));
        }
        if (!authService.hasDecryptForScreen(httpRequest, screenId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("해당 로그 타입에 대한 복호화 권한이 없습니다.", "FUNCTION_NOT_ALLOWED"));
        }

        String guid = request.get("guid");
        String status = request.get("status");

        if (!"java_fw_imglog".equals(logType)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE"));
        }
        if (guid == null || guid.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("GUID는 필수입니다.", "MISSING_GUID"));
        }
        String st = DecryptionRowKey.normalizeStatus(status);
        if (st.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("status는 필수입니다 (java_fw_imglog 복합 키).", "MISSING_STATUS"));
        }

        if (!decryptionAllowedService.isAllowed(currentUserId, screenId, guid, st)) {
            log.warn("복호화 거부(decryption-allowed): currentUserId={}, guid={}, status={}", currentUserId, guid, st);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("복호화 승인 후 이용 가능합니다. 이번 검색에서 '복호화 승인 요청'을 진행해 주세요.", "DECRYPTION_NOT_APPROVED"));
        }

        log.info("🔓 복호화 요청: logType={}, guid={}, status={}", logType, guid, st);

        try {
            Map<String, Object> decryptedData = logDbService.decryptRow(logType, guid, st);
            return ResponseEntity.ok(ApiResponse.success(decryptedData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(e.getMessage(), "MISSING_STATUS"));
        } catch (Exception e) {
            log.error("복호화 중 오류 발생: currentUserId={}, guid={}, status={}", currentUserId, guid, st, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.failure("복호화 실패: " + e.getMessage(), "DECRYPTION_FAILED"));
        }
    }

}

