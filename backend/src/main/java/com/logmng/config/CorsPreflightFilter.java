package com.logmng.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * OPTIONS preflight를 필터 체인 최상단에서 처리하여,
 * CORS 헤더와 200을 반환하고 chain.doFilter를 호출하지 않음.
 * (CorsFilterConfig에서 FilterRegistrationBean으로 HIGHEST_PRECEDENCE 등록)
 */
public class CorsPreflightFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000", "http://localhost:3001",
            "http://127.0.0.1:3000", "http://127.0.0.1:3001");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
