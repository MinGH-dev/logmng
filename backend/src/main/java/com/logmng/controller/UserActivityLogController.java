package com.logmng.controller;

import com.logmng.constants.ActivityActionType;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.PrivilegedRevealRequest;
import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.UserActivityLogService;
import com.logmng.util.ActivityLogAuditAuthorization;
import com.logmng.util.ActivityLogAuditMasking;
import com.logmng.util.DepartmentScopeHelper;
import com.logmng.util.ScopeHelper;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 사용자 활동 이력 컨트롤러.
 * Scope enforcement: is_system_admin=false and scope='self' → override userId with current user; getActivityLogDetail enforces ownership.
 */
@RestController
@RequestMapping("/api/activity-log")
public class UserActivityLogController {
    
    private static final Logger log = LoggerFactory.getLogger(UserActivityLogController.class);
    
    private final UserActivityLogService userActivityLogService;
    private final AuthService authService;
    private final DataSource dataSource;
    private final AppUserResolver appUserResolver;

    public UserActivityLogController(UserActivityLogService userActivityLogService, AuthService authService, DataSource dataSource, AppUserResolver appUserResolver) {
        this.userActivityLogService = userActivityLogService;
        this.authService = authService;
        this.dataSource = dataSource;
        this.appUserResolver = appUserResolver;
    }
    
    /**
     * Canonical activity type codes for the activity-log filter (code + label).
     * GET /api/activity-log/action-types
     */
    @GetMapping("/action-types")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getActionTypes(
            HttpServletRequest httpRequest) {
        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        List<Map<String, String>> data = ActivityActionType.filterDropdownOptions();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 사용자 활동 이력 검색
     * POST /api/activity-log/search
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<UserActivityLogResponse>> searchActivityLogs(
            @RequestBody UserActivityLogSearchRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("🔍 사용자 활동 이력 검색 요청 수신");
        log.debug("🔍 요청 파라미터: {}", request);
        
        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (request.getUserId() != null) {
            String username = appUserResolver.getUsernameById(request.getUserId());
            if (username == null) {
                throw CustomException.badRequest("유효하지 않은 userId입니다.", "INVALID_INPUT");
            }
            request.setUserIdForFilter(username);
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.ACTIVITY_LOG, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String currentUserId = ScopeHelper.normalizeOptionalParam(userInfo.getUsername());
        if (("self".equals(scope) || "team".equals(scope)) && currentUserId == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        List<String> teamUserIds = "team".equals(scope)
                ? DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, currentUserId)
                : null;
        ScopeHelper.applyActivityLogSearchScope(request, scope, currentUserId, teamUserIds);
        
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);
        boolean maskIp = !Boolean.TRUE.equals(userInfo.getIsSystemAdmin());
        if (response.getData() != null) {
            for (Map<String, Object> row : response.getData()) {
                ActivityLogAuditMasking.applyToRow(row, maskIp);
            }
        }

        ApiResponse<UserActivityLogResponse> apiResponse = ApiResponse.success(response);
        return ResponseEntity.ok(apiResponse);
    }
    
    /**
     * 사용자 활동 이력 상세 조회
     * GET /api/activity-log/{id} (numeric id only — avoids capturing literal paths e.g. /action-types)
     * When scope='self', verifies ownership; returns 403 if not owner.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActivityLogDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        
        log.debug("🔍 사용자 활동 이력 상세 조회: id={}", id);
        
        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.ACTIVITY_LOG, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String currentUserForOwnership = "self".equals(scope) ? userInfo.getUsername() : null;
        List<String> allowedUserIdsForTeam = "team".equals(scope) ? DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, userInfo.getUsername()) : null;
        
        Map<String, Object> data = userActivityLogService.getActivityLogDetail(id, currentUserForOwnership, allowedUserIdsForTeam);
        ActivityLogAuditMasking.applyToRow(data, !Boolean.TRUE.equals(userInfo.getIsSystemAdmin()));

        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/activity-log/{id}/privileged-reveal — full copy body after access-audit insert.
     */
    @PostMapping("/{id:\\d+}/privileged-reveal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> privilegedReveal(
            @PathVariable Long id,
            @RequestBody PrivilegedRevealRequest body,
            HttpServletRequest httpRequest) {
        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.ACTIVITY_LOG, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String currentUserForOwnership = "self".equals(scope) ? userInfo.getUsername() : null;
        List<String> allowedUserIdsForTeam = "team".equals(scope)
                ? DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, userInfo.getUsername())
                : null;

        String clientIp = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        Map<String, Object> data = userActivityLogService.privilegedRevealCopyBody(
                id,
                body != null ? body.getRevealKind() : null,
                userInfo,
                clientIp,
                ua,
                currentUserForOwnership,
                allowedUserIdsForTeam);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * GET /api/activity-log/access-audit — paginated access-audit rows (TC-07 scope).
     */
    @GetMapping("/access-audit")
    public ResponseEntity<ApiResponse<UserActivityLogResponse>> searchAccessAudit(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long accessorUserId,
            @RequestParam(required = false) Long targetActivityLogId,
            @RequestParam(required = false) String accessType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest) {

        LoginResponse userInfo = authService.getCurrentUserInfo(httpRequest);
        if (userInfo == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!ActivityLogAuditAuthorization.canQueryAccessAudit(userInfo)) {
            throw CustomException.forbidden("접근 감사 목록을 조회할 권한이 없습니다.", "ACCESS_AUDIT_FORBIDDEN");
        }

        Map<String, String> scopes = userInfo.getScreenScopes();
        String scope = ScopeHelper.resolveScope(ScreenConstants.ACTIVITY_LOG, Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scopes != null ? scopes : java.util.Collections.emptyMap());
        String currentUsername = userInfo.getUsername();
        List<String> teamUserIds = "team".equals(scope)
                ? DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, currentUsername)
                : null;

        LocalDate start = parseQueryLocalDate(startDate);
        LocalDate end = parseQueryLocalDate(endDate);

        UserActivityLogResponse data = userActivityLogService.searchAccessAudit(
                start,
                end,
                accessorUserId,
                targetActivityLogId,
                accessType,
                page != null ? page : 1,
                pageSize != null ? pageSize : 20,
                sortDirection,
                Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                scope,
                currentUsername,
                teamUserIds);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private static LocalDate parseQueryLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String t = raw.trim();
            if (t.length() >= 10) {
                t = t.substring(0, 10);
            }
            return LocalDate.parse(t);
        } catch (Exception e) {
            throw CustomException.badRequest("날짜 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}





