package com.logmng.controller;

import com.logmng.annotation.ActivityLog;
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
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
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
        
        log.debug("로그인 요청: 사용자명={}", request.getUsername());
        
        LoginResponse loginResponse = authService.login(request, httpRequest);
        
        // 세션에 사용자 정보 및 isSystemAdmin 저장 (관리자 권한 판단용, req 20250303)
        jakarta.servlet.http.HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userId", loginResponse.getUsername());
        session.setAttribute("username", loginResponse.getUsername());
        session.setAttribute("isSystemAdmin", Boolean.TRUE.equals(loginResponse.getIsSystemAdmin()));
        session.setAttribute("allowedScreenIds", loginResponse.getAllowedScreenIds());
        session.setAttribute("screenScopes", loginResponse.getScreenScopes());
        log.info("세션 저장 완료: userId={}, isSystemAdmin={}, sessionId={}",
                loginResponse.getUsername(), loginResponse.getIsSystemAdmin(), session.getId());
        
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
     */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAuth(HttpServletRequest httpRequest) {
        log.debug("인증 상태 확인 요청");
        boolean authenticated = authService.checkAuth(httpRequest);
        
        Map<String, Object> data = new HashMap<>();
        data.put("authenticated", authenticated);
        data.put("message", authenticated ? "인증되었습니다." : "인증되지 않았습니다.");
        if (authenticated) {
            LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
            if (userInfo != null) {
                data.put("username", userInfo.getUsername());
                data.put("isSystemAdmin", userInfo.getIsSystemAdmin());
                data.put("allowedScreenIds", userInfo.getAllowedScreenIds());
                data.put("screenScopes", userInfo.getScreenScopes());
            }
        }
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
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
}

