package com.logmng.controller;

import com.logmng.constants.ActivityActionType;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.UserActivityLogService;
import com.logmng.util.DepartmentScopeHelper;
import com.logmng.util.ScopeHelper;
import org.slf4j.Logger;

import javax.sql.DataSource;
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
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);
        return ResponseEntity.ok(response);
    }
}





