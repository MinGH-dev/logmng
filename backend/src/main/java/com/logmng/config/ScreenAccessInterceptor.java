package com.logmng.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.diagnostic.PermissionGroupScreenDiagnosticLog;
import com.logmng.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    /** Path pattern (regex) -> required screen_id(s). Order: more specific first. */
    private static final List<PathScreenRule> PATH_SCREEN_RULES = List.of(
            new PathScreenRule("^/api/departments/user-permission-hierarchy$", List.of(ScreenConstants.USER_MANAGEMENT, ScreenConstants.USER_PERMISSION_HIERARCHY)),
            new PathScreenRule("^/api/departments.*", List.of(ScreenConstants.DEPARTMENT_APPROVERS, ScreenConstants.USER_PERMISSION_HIERARCHY)),
            new PathScreenRule("^/api/permission-groups.*", List.of(ScreenConstants.USER_MANAGEMENT, ScreenConstants.USER_PERMISSION_HIERARCHY)),
            new PathScreenRule("^/api/search-history/pending.*", List.of(ScreenConstants.PENDING_APPROVALS)),
            new PathScreenRule("^/api/search-history/[^/]+/approve.*",
                    List.of(ScreenConstants.PENDING_APPROVALS, ScreenConstants.SEARCH_HISTORY)),
            new PathScreenRule("^/api/search-history/[^/]+/reject.*",
                    List.of(ScreenConstants.PENDING_APPROVALS, ScreenConstants.SEARCH_HISTORY)),
            new PathScreenRule("^/api/search-history.*", List.of(ScreenConstants.SEARCH_HISTORY)),
            new PathScreenRule("^/api/activity-log/\\d+/privileged-reveal$",
                    List.of(ScreenConstants.ACTIVITY_LOG, ScreenConstants.ACTIVITY_LOG_ACCESS_AUDIT)),
            new PathScreenRule("^/api/activity-log/access-audit$",
                    List.of(ScreenConstants.ACTIVITY_LOG, ScreenConstants.ACTIVITY_LOG_ACCESS_AUDIT)),
            new PathScreenRule("^/api/activity-log.*", List.of(ScreenConstants.ACTIVITY_LOG)),
            new PathScreenRule("^/api/statistics.*", List.of(ScreenConstants.STATISTICS)),
            new PathScreenRule("^/api/users.*", List.of(ScreenConstants.USER_MANAGEMENT)),
            new PathScreenRule("^/api/provisioning.*", List.of(ScreenConstants.USER_MANAGEMENT, ScreenConstants.USER_PERMISSION_HIERARCHY)),
            new PathScreenRule("^/api/logs/db-refactored.*", List.of(ScreenConstants.PB_FEPLOG, ScreenConstants.PB_FEP_LOG_SEARCH, ScreenConstants.JAVA_FW_IMAGELOG)),
            new PathScreenRule("^/api/logs/decrypt.*", List.of(ScreenConstants.PB_FEPLOG, ScreenConstants.PB_FEP_LOG_SEARCH, ScreenConstants.JAVA_FW_IMAGELOG)),
            new PathScreenRule("^/api/search.*", List.of(ScreenConstants.PB_FEPLOG, ScreenConstants.PB_FEP_LOG_SEARCH, ScreenConstants.JAVA_FW_IMAGELOG))
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Value("${app.diagnostic.permission-group-screen:false}")
    private boolean diagnosticPermissionGroupScreen;

    private static final Pattern PERMISSION_GROUPS_API = Pattern.compile("^/api/permission-groups.*");

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
        if (Boolean.TRUE.equals(userInfo.getIsSystemAdmin())) {
            return true;
        }
        List<String> allowedScreenIds = userInfo.getAllowedScreenIds();
        if (allowedScreenIds == null || allowedScreenIds.isEmpty()) {
            log.warn("Screen access denied (zero permissions): path={} user={}", path, userInfo.getUsername());
            sendForbidden(response);
            return false;
        }
        if (isSearchHistoryReadGet(request.getMethod(), path)) {
            if (allowSearchHistoryListOrDetailRead(userInfo)) {
                return true;
            }
            sendForbidden(response);
            return false;
        }
        List<String> requiredScreens = findRequiredScreens(path);
        if (requiredScreens == null || requiredScreens.isEmpty()) {
            return true;
        }
        List<String> allowed = allowedScreenIds;
        boolean hasAccess = requiredScreens.stream().anyMatch(allowed::contains);
        if (hasAccess) {
            return true;
        }
        if (PERMISSION_GROUPS_API.matcher(path).matches()) {
            PermissionGroupScreenDiagnosticLog.screenAccessDenyPermissionGroups(
                    diagnosticPermissionGroupScreen,
                    path,
                    requiredScreens,
                    userInfo.getUserId(),
                    "missing_required_screen_in_session");
        }
        log.warn("Screen access denied: path={} requiredScreens={} user={}", path, requiredScreens, userInfo.getUsername());
        sendForbidden(response);
        return false;
    }

    private static boolean isSearchHistoryReadGet(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/api/search-history".equals(path)) {
            return true;
        }
        return path != null && path.startsWith("/api/search-history/")
                && path.matches("^/api/search-history/\\d+$");
    }

    /**
     * GET list/detail: search-history screen OR pending-approvals with effective read (docs/contract.md §6.1.2).
     */
    private boolean allowSearchHistoryListOrDetailRead(LoginResponse userInfo) {
        List<String> allowed = userInfo.getAllowedScreenIds();
        if (allowed != null && allowed.contains(ScreenConstants.SEARCH_HISTORY)) {
            return true;
        }
        if (allowed != null && allowed.contains(ScreenConstants.PENDING_APPROVALS)) {
            return authService.hasEffectiveReadForPendingApprovals(userInfo);
        }
        return false;
    }

    private List<String> findRequiredScreens(String path) {
        for (PathScreenRule rule : PATH_SCREEN_RULES) {
            if (rule.pattern.matcher(path).matches()) {
                return rule.screenIds;
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
        final List<String> screenIds;

        PathScreenRule(String regex, List<String> screenIds) {
            this.pattern = Pattern.compile(regex);
            this.screenIds = screenIds;
        }
    }
}
