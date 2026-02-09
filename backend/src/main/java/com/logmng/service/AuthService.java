package com.logmng.service;

import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 인증 서비스
 */
@Service
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    private final IpUtil ipUtil;
    
    @Value("${app.security.admin-id:admin}")
    private String adminId;
    
    @Value("${app.security.admin-pw:admin123}")
    private String adminPw;
    
    @Value("${app.security.authorized-ips:127.0.0.1,localhost,0:0:0:0:0:0:0:1}")
    private String authorizedIPs;
    
    public AuthService(IpUtil ipUtil) {
        this.ipUtil = ipUtil;
    }
    
    /**
     * 로그인 처리
     * 
     * @param request 로그인 요청
     * @param httpRequest HTTP 요청 (IP 추출용)
     * @return 로그인 응답
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String password = request.getPassword();
        
        // 클라이언트 IP 추출
        String clientIP = ipUtil.getClientIP(httpRequest);
        log.info("🔍 로그인 시도 - IP: {}, 사용자명: {}", clientIP, username);
        
        // IP 기반 접근 제어
        if (!ipUtil.isAuthorizedIP(clientIP, authorizedIPs)) {
            log.warn("❌ 인가되지 않은 IP에서 로그인 시도: {}", clientIP);
            throw CustomException.forbidden(
                    "접근이 제한된 IP 주소입니다. 시스템 관리자에게 접근 권한을 요청하세요.",
                    "IP_ACCESS_DENIED"
            );
        }
        
        // 사용자명 확인
        if (!username.equals(adminId)) {
            log.warn("❌ 로그인 실패: 잘못된 사용자명 ({})", username);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }
        
        // 비밀번호 확인 (평문 비교 - 간단한 인증)
        if (!password.equals(adminPw)) {
            log.warn("❌ 로그인 실패: 잘못된 비밀번호 ({})", username);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }
        
        // 로그인 성공
        log.info("✅ 로그인 성공: {} (IP: {})", username, clientIP);
        
        LoginResponse response = new LoginResponse();
        response.setUsername(username);
        response.setLoginTime(LocalDateTime.now());
        response.setClientIP(clientIP);
        return response;
    }
    
    /**
     * 로그아웃 처리
     * 
     * @return 성공 여부
     */
    public boolean logout() {
        log.info("✅ 로그아웃 요청");
        return true;
    }
    
    /**
     * 인증 상태 확인
     * 
     * @return 인증 여부 (현재는 항상 false - 세션 미구현)
     */
    public boolean checkAuth() {
        // 실제 프로덕션에서는 JWT 토큰이나 세션을 검증해야 함
        return false;
    }
}





