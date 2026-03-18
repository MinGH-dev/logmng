package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
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
        if (!authService.hasDecryptForMain(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("복호화 기능 권한이 없습니다. 권한 그룹에서 검색하기 화면의 복호화 권한이 부여되어 있어야 합니다.", "FUNCTION_NOT_ALLOWED"));
        }
        // 복호화는 "현재 검색에 대한 승인"만 허용. searchHistoryId가 해당 사용자(user_id=app_user.id)·승인·미만료인지 검사 (req 20260316).
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

        if (!decryptionAllowedService.isAllowed(currentUserId, ScreenConstants.MAIN, guid)) {
            log.warn("복호화 거부(decryption-allowed): currentUserId={}, guid={}", currentUserId, guid);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("복호화 승인 후 이용 가능합니다. 이번 검색에서 '복호화 승인 요청'을 진행해 주세요.", "DECRYPTION_NOT_APPROVED"));
        }

        log.info("🔓 복호화 요청: logType={}, guid={}, status={}", logType, guid, status);

        try {
            Map<String, Object> decryptedData = logDbService.decryptRow(logType, guid, status);
            return ResponseEntity.ok(ApiResponse.success(decryptedData));
        } catch (Exception e) {
            log.error("복호화 중 오류 발생: currentUserId={}, guid={}, status={}", currentUserId, guid, status, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.failure("복호화 실패: " + e.getMessage(), "DECRYPTION_FAILED"));
        }
    }

}

