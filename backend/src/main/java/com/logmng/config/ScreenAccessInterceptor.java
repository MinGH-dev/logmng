package com.logmng.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
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
 * Screen-based access validation per specs/permission-group-hierarchy.spec.yaml §4.3.
 * Validates that non-ADMIN users have the required screen for the API path.
 * ADMIN bypasses; paths without mapping are allowed.
 */
@Component
public class ScreenAccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ScreenAccessInterceptor.class);

    private static final List<Pattern> EXCLUDE_PATTERNS = List.of(
            Pattern.compile("^/api/auth/.*"),
            Pattern.compile("^/api/health$"),
            Pattern.compile("^/api/db/.*"),
            Pattern.compile("^/api/log-types.*")
    );

    /** Path pattern (regex) -> required screen_id. Order: more specific first. */
    private static final List<PathScreenRule> PATH_SCREEN_RULES = List.of(
            new PathScreenRule("^/api/departments/user-permission-hierarchy$", ScreenConstants.USER_PERMISSION_HIERARCHY),
            new PathScreenRule("^/api/departments.*", ScreenConstants.USER_PERMISSION_HIERARCHY),
            new PathScreenRule("^/api/permission-groups.*", ScreenConstants.USER_PERMISSION_HIERARCHY),
            new PathScreenRule("^/api/search-history/pending.*", ScreenConstants.PENDING_APPROVALS),
            new PathScreenRule("^/api/search-history/[^/]+/approve.*", ScreenConstants.PENDING_APPROVALS),
            new PathScreenRule("^/api/search-history/[^/]+/reject.*", ScreenConstants.PENDING_APPROVALS),
            new PathScreenRule("^/api/search-history.*", ScreenConstants.SEARCH_HISTORY),
            new PathScreenRule("^/api/activity-log.*", ScreenConstants.ACTIVITY_LOG),
            new PathScreenRule("^/api/statistics.*", ScreenConstants.STATISTICS),
            new PathScreenRule("^/api/users.*", ScreenConstants.USER_MANAGEMENT),
            new PathScreenRule("^/api/logs/db-refactored.*", ScreenConstants.MAIN),
            new PathScreenRule("^/api/logs/decrypt.*", ScreenConstants.MAIN),
            new PathScreenRule("^/api/search.*", ScreenConstants.MAIN)
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public ScreenAccessInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        for (Pattern p : EXCLUDE_PATTERNS) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        LoginResponse userInfo = authService.getCurrentUserInfo(request);
        if (userInfo == null) {
            return true; // AuthInterceptor should have rejected; allow through
        }
        if ("ADMIN".equals(userInfo.getRole())) {
            return true;
        }
        String requiredScreen = findRequiredScreen(path);
        if (requiredScreen == null) {
            return true;
        }
        List<String> allowed = userInfo.getAllowedScreenIds();
        if (allowed != null && allowed.contains(requiredScreen)) {
            return true;
        }
        log.warn("Screen access denied: path={} requiredScreen={} user={}", path, requiredScreen, userInfo.getUsername());
        sendForbidden(response);
        return false;
    }

    private String findRequiredScreen(String path) {
        for (PathScreenRule rule : PATH_SCREEN_RULES) {
            if (rule.pattern.matcher(path).matches()) {
                return rule.screenId;
            }
        }
        return null;
    }

    private void sendForbidden(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.failure("해당 화면에 대한 접근 권한이 없습니다.", "FORBIDDEN");
        try {
            response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
        } catch (IOException e) {
            log.error("403 응답 작성 실패", e);
        }
    }

    private static class PathScreenRule {
        final Pattern pattern;
        final String screenId;

        PathScreenRule(String regex, String screenId) {
            this.pattern = Pattern.compile(regex);
            this.screenId = screenId;
        }
    }
}
