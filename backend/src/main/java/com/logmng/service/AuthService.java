package com.logmng.service;

import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 인증 서비스. 로그인은 app_user 테이블 기준(DataSource).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IpUtil ipUtil;
    private final DataSource dataSource;

    @Value("${app.security.authorized-ips:127.0.0.1,localhost,0:0:0:0:0:0:0:1}")
    private String authorizedIPs;

    public AuthService(IpUtil ipUtil, DataSource dataSource) {
        this.ipUtil = ipUtil;
        this.dataSource = dataSource;
    }

    /**
     * 로그인 처리. app_user에서 username/password_hash/role 조회 후 비밀번호 검증.
     * (개발 환경: password_hash에 평문 저장 시 그대로 비교)
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String password = request.getPassword();

        String clientIP = ipUtil.getClientIP(httpRequest);
        log.info("로그인 시도 - IP: {}, 사용자명: {}", clientIP, username);

        if (!ipUtil.isAuthorizedIP(clientIP, authorizedIPs)) {
            log.warn("인가되지 않은 IP에서 로그인 시도: {}", clientIP);
            throw CustomException.forbidden(
                    "접근이 제한된 IP 주소입니다. 시스템 관리자에게 접근 권한을 요청하세요.",
                    "IP_ACCESS_DENIED"
            );
        }

        String passwordHash = null;
        String role = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username, password_hash, role FROM app_user WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("로그인 실패: 사용자 없음 ({})", username);
                        throw CustomException.unauthorized(
                                "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                                "INVALID_CREDENTIALS"
                        );
                    }
                    passwordHash = rs.getString("password_hash");
                    role = rs.getString("role");
                }
            }
        } catch (SQLException e) {
            log.error("로그인 조회 실패: username={}", username, e);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }
        if (passwordHash == null || role == null) {
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        // 개발: password_hash에 평문 저장 시 비교. 추후 BCrypt 등으로 교체 가능.
        if (!password.equals(passwordHash)) {
            log.warn("로그인 실패: 비밀번호 불일치 ({})", username);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        log.info("로그인 성공: {} role={} (IP: {})", username, role, clientIP);

        LoginResponse response = new LoginResponse();
        response.setUsername(username);
        response.setLoginTime(LocalDateTime.now());
        response.setClientIP(clientIP);
        response.setRole(role);
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
     * 인증 상태 확인 (세션 기반)
     *
     * @param request HTTP 요청 (세션 확인용, null이면 false)
     * @return 인증 여부 (세션에 userId 또는 username이 있으면 true)
     */
    public boolean checkAuth(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        try {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session == null) {
                return false;
            }
            Object userId = session.getAttribute("userId");
            Object username = session.getAttribute("username");
            return (userId != null && !userId.toString().isEmpty())
                    || (username != null && !username.toString().isEmpty());
        } catch (Exception e) {
            log.debug("인증 확인 중 오류: {}", e.getMessage());
            return false;
        }
    }
}





