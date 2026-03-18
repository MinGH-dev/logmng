package com.logmng.controller;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptionAllowedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * GET /api/decrypt/allowed — decryption-allowed list for current user and screen.
 * Req: docs/requirements/20260318-decryption-allowed-store-and-decrypt-ui.md.
 * Contract: docs/api-definition.md §10.1.
 */
@RestController
@RequestMapping("/api/decrypt")
public class DecryptAllowedController {

    private final AuthService authService;
    private final DecryptionAllowedService decryptionAllowedService;

    public DecryptAllowedController(AuthService authService, DecryptionAllowedService decryptionAllowedService) {
        this.authService = authService;
        this.decryptionAllowedService = decryptionAllowedService;
    }

    /**
     * GET /api/decrypt/allowed?screen=pb-feplog | java-fw-imagelog (or main for backward compat)
     * Returns { screen, validUntil, guids } for the current user. Requires that screen and decrypt permission for it.
     */
    @GetMapping("/allowed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllowed(
            @RequestParam(value = "screen", required = false) String screen,
            HttpServletRequest httpRequest) {

        LoginResponse currentUser = authService.getCurrentUserInfo(httpRequest);
        if (currentUser == null || currentUser.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        if (screen == null || screen.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("screen 쿼리는 필수입니다.", "INVALID_SCREEN_ID"));
        }
        String screenTrim = screen.trim();
        if (!ScreenConstants.isValid(screenTrim)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("지원하지 않는 화면 ID입니다.", "INVALID_SCREEN_ID"));
        }
        if (!ScreenConstants.supportsDecrypt(screenTrim)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("해당 화면은 복호화 허용 목록을 지원하지 않습니다.", "INVALID_SCREEN_ID"));
        }
        if (!authService.hasDecryptForScreen(httpRequest, screenTrim)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("해당 화면에 대한 복호화 권한이 없습니다.", "FUNCTION_NOT_ALLOWED"));
        }

        Map<String, Object> data = decryptionAllowedService.getAllowed(currentUser.getUserId(), screenTrim);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
