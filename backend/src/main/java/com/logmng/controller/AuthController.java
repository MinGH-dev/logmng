package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
import com.logmng.config.AuthProperties;
import com.logmng.dto.request.ChangeMyPasswordRequest;
import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 컨트롤러
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String LOGIN_MODE_LOCAL = "local";
    private static final String LOGIN_MODE_AD = "ad";
    
    private final AuthService authService;
    private final AuthProperties authProperties;
    
    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }
    
    /**
     * 로그인
     * POST /api/auth/login
     */
    @ActivityLog(actionType = "LOGIN", description = "로그인", includeParams = false)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, LoginResponse>>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.debug("로그인 요청: 사용자 ID={}", request.getUserId());
        
        LoginResponse loginResponse = authService.login(request, httpRequest);
        
        // 세션에 userId (Long, app_user.id) 및 권한 정보 저장 (req 20250303, 계약: 로그인은 id만 사용)
        jakarta.servlet.http.HttpSession session = httpRequest.getSession(true);
        Long numericUserId = loginResponse.getUserId() != null ? loginResponse.getUserId()
                : (loginResponse.getSelfContext() != null ? loginResponse.getSelfContext().getUserId() : null);
        if (numericUserId != null) {
            session.setAttribute("userId", numericUserId);
        }
        if (loginResponse.getUsername() != null) {
            session.setAttribute("username", loginResponse.getUsername());
        }
        session.setAttribute("isSystemAdmin", Boolean.TRUE.equals(loginResponse.getIsSystemAdmin()));
        session.setAttribute("allowedScreenIds", loginResponse.getAllowedScreenIds());
        session.setAttribute("screenScopes", loginResponse.getScreenScopes());
        session.setAttribute("screenFunctions", loginResponse.getScreenFunctions());
        log.info("세션 저장 완료: userId={}, isSystemAdmin={}, sessionId={}",
                numericUserId, loginResponse.getIsSystemAdmin(), session.getId());
        
        Map<String, LoginResponse> data = new HashMap<>();
        data.put("user", loginResponse);
        
        ApiResponse<Map<String, LoginResponse>> response = 
                ApiResponse.success(data, "로그인에 성공했습니다.");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 로그아웃
     * POST /api/auth/logout
     */
    @ActivityLog(actionType = "LOGOUT", description = "로그아웃", includeParams = false)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.debug("로그아웃 요청");
        authService.logout();
        
        ApiResponse<Void> response = ApiResponse.success(null, "로그아웃되었습니다.");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 인증 상태 확인
     * GET /api/auth/check
     * Req 20260316: Defensive null checks and try-catch so response build/serialization never cause 500.
     */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAuth(HttpServletRequest httpRequest) {
        log.debug("인증 상태 확인 요청");
        try {
            boolean authenticated = authService.checkAuth(httpRequest);
            Map<String, Object> data = new HashMap<>();
            data.put("authenticated", authenticated);
            data.put("message", authenticated ? "인증되었습니다." : "인증되지 않았습니다.");
            if (authenticated) {
                LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
                if (userInfo != null) {
                    if (userInfo.getUsername() != null) {
                        data.put("username", userInfo.getUsername());
                    }
                    if (userInfo.getIsSystemAdmin() != null) {
                        data.put("isSystemAdmin", userInfo.getIsSystemAdmin());
                    }
                    if (userInfo.getAllowedScreenIds() != null) {
                        data.put("allowedScreenIds", userInfo.getAllowedScreenIds());
                    }
                    if (userInfo.getScreenScopes() != null) {
                        data.put("screenScopes", userInfo.getScreenScopes());
                    }
                    if (userInfo.getScreenFunctions() != null) {
                        data.put("screenFunctions", userInfo.getScreenFunctions());
                    }
                    if (userInfo.getSelfContext() != null) {
                        data.put("selfContext", userInfo.getSelfContext());
                    }
                }
            }
            ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("인증 상태 확인 중 예외 발생, authenticated=false 반환", e);
            Map<String, Object> data = new HashMap<>();
            data.put("authenticated", false);
            data.put("message", "인증되지 않았습니다.");
            return ResponseEntity.ok(ApiResponse.success(data));
        }
    }

    /**
     * 로그인 모드 설정 조회
     * GET /api/auth/config
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuthConfig() {
        long startedAt = System.nanoTime();
        log.debug("[diag-auth-config] AuthController entry /api/auth/config");
        String rawMode = authProperties != null && authProperties.getLogin() != null
                ? authProperties.getLogin().getMode()
                : null;
        String resolvedMode = normalizeLoginMode(rawMode);

        Map<String, Object> data = new HashMap<>();
        // Keep both keys for backward compatibility with existing frontend callers.
        data.put("loginMode", resolvedMode);
        data.put("authLoginMode", resolvedMode);

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("[diag-auth-config] AuthController exit /api/auth/config rawMode={} resolvedMode={} elapsedMs={}",
                rawMode, resolvedMode, elapsedMs);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private String normalizeLoginMode(String mode) {
        if (mode == null) {
            return LOGIN_MODE_LOCAL;
        }
        String trimmed = mode.trim().toLowerCase();
        if (LOGIN_MODE_AD.equals(trimmed)) {
            return LOGIN_MODE_AD;
        }
        return LOGIN_MODE_LOCAL;
    }
    
    /**
     * 현재 사용자 정보 (username, role, allowedScreenIds)
     * GET /api/auth/me — per specs/permission-group-hierarchy.spec.yaml §4.2
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, LoginResponse>>> me(HttpServletRequest httpRequest) {
        log.debug("인증 사용자 정보 요청");
        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            return ResponseEntity.status(401).body(
                    ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED"));
        }
        Map<String, LoginResponse> data = new HashMap<>();
        data.put("user", userInfo);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 본인 비밀번호 변경 (local 모드·테이블 비밀번호만). POST /api/auth/me/password
     */
    @ActivityLog(
            actionType = "PASSWORD_SELF_CHANGE",
            description = "본인 비밀번호 변경",
            includeParams = false,
            includeResponse = false)
    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Valid @RequestBody ChangeMyPasswordRequest body,
            HttpServletRequest httpRequest) {
        authService.changeOwnPassword(httpRequest, body);
        return ResponseEntity.ok(ApiResponse.success(null, "비밀번호가 변경되었습니다."));
    }
}

