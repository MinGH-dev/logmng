package com.logmng.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.response.ApiResponse;
import com.logmng.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 인증 필요 API에 대해 세션을 검사한다.
 * 미인증 시 401과 JSON 메시지를 반환한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final String API_PREFIX = "/api/";
    private static final List<String> CORS_ALLOWED_ORIGINS = List.of(
            "http://localhost:3000", "http://localhost:3001",
            "http://127.0.0.1:3000", "http://127.0.0.1:3001");
    private static final List<Pattern> EXCLUDE_PATTERNS = List.of(
            Pattern.compile("^/api/auth/.*"),
            Pattern.compile("^/api/health$"),
            Pattern.compile("^/api/db/test$"),
            Pattern.compile("^/api/db/schema$"),
            Pattern.compile("^/api/log-types$"),
            Pattern.compile("^/api/log-types/[^/]+$"),
            Pattern.compile("^/api/log-types/[^/]+/fields$")
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight(OPTIONS): 인증 검사 없이 200 + CORS 헤더만 보내고 chain 중단 (컨트롤러 미호출)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith(API_PREFIX)) {
            addCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            return true;
        }
        for (Pattern p : EXCLUDE_PATTERNS) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        if (!authService.checkAuth(request)) {
            log.warn("미인증 접근 차단: {} {}", request.getMethod(), path);
            sendUnauthorized(response);
            return false;
        }
        return true;
    }

    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && CORS_ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private void sendUnauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.failure("로그인이 필요합니다.", "UNAUTHORIZED");
        try {
            response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
        } catch (IOException e) {
            log.error("401 응답 작성 실패", e);
        }
    }
}
