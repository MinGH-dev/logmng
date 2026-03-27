package com.logmng.controller;

import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.PermissionGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.logmng.diagnostic.PermissionGroupScreenDiagnosticLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Permission group CRUD and user-group assignment. §14. Admin-only.
 */
@RestController
@RequestMapping("/api/permission-groups")
public class PermissionGroupController {

    private static final Logger log = LoggerFactory.getLogger(PermissionGroupController.class);

    private final PermissionGroupService permissionGroupService;
    private final AuthService authService;
    private final AppUserResolver appUserResolver;

    @Value("${app.diagnostic.permission-group-screen:false}")
    private boolean diagnosticPermissionGroupScreen;

    public PermissionGroupController(PermissionGroupService permissionGroupService,
                                     AuthService authService,
                                     AppUserResolver appUserResolver) {
        this.permissionGroupService = permissionGroupService;
        this.authService = authService;
        this.appUserResolver = appUserResolver;
    }

    /** Parse request body userId (number or numeric string) to Long. */
    private static Long parseUserIdNumeric(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) return null;
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /** Allows isSystemAdmin OR allowedScreenIds contains user-management or user-permission-hierarchy. Per spec §4.3. */
    private void requireUserManagementAccess(HttpServletRequest request) {
        if (!authService.checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!authService.canAccessUserManagementView(request)) {
            log.info("Permission group API access denied: no user-management or user-permission-hierarchy");
            throw CustomException.forbidden("관리자만 권한 그룹을 관리할 수 있습니다.", "FORBIDDEN");
        }
    }

    /** Requires write function for user-management or user-permission-hierarchy. Per spec §4.4. */
    private void requireWriteForManagement(HttpServletRequest request) {
        if (!authService.hasWriteForManagementScreens(request)) {
            log.info("Permission group write API denied: no write function");
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
    }

    /**
     * GET /api/permission-groups — list all. §14.1
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionGroupResponse>>> list(HttpServletRequest request) {
        requireUserManagementAccess(request);
        PermissionGroupScreenDiagnosticLog.debug(diagnosticPermissionGroupScreen, "GET_list_enter", "after_auth_checks");
        List<PermissionGroupResponse> data = permissionGroupService.listAll();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * POST /api/permission-groups — create. §14.2
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionGroupResponse>> create(
            @Valid @RequestBody PermissionGroupCreateRequest body,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        requireWriteForManagement(request);
        PermissionGroupResponse data = permissionGroupService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * GET /api/permission-groups/{id} — get one. §14.3
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionGroupResponse>> getOne(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        PermissionGroupResponse data = permissionGroupService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * PUT /api/permission-groups/{id} — update. §14.4
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionGroupResponse>> update(
            @PathVariable Long id,
            @RequestBody PermissionGroupUpdateRequest body,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        requireWriteForManagement(request);
        PermissionGroupResponse data = permissionGroupService.update(id, body);
        String changeReason = body.getChangeReason();
        if (changeReason != null && !changeReason.isBlank()) {
            LoginResponse actor = authService.getCurrentUserInfo(request);
            Long actorUserId = actor != null ? actor.getUserId() : null;
            String actorUsername = actor != null ? actor.getUsername() : null;
            String trimmed = changeReason.trim();
            int logLen = Math.min(trimmed.length(), 500);
            log.info(
                    "Permission group updated: id={}, actorUserId={}, actorUsername={}, changeReasonPrefix={}",
                    id, actorUserId, actorUsername, trimmed.substring(0, logLen));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * DELETE /api/permission-groups/{id} — delete. §14.5
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        requireWriteForManagement(request);
        permissionGroupService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * POST /api/permission-groups/{id}/users — assign user. §14.6. Body userId = numeric app_user.id (req 20260316).
     */
    @PostMapping("/{id}/users")
    public ResponseEntity<ApiResponse<AssignUserToGroupResponse>> assignUser(
            @PathVariable Long id,
            @Valid @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        requireWriteForManagement(request);
        Object userIdObj = body != null ? body.get("userId") : null;
        if (userIdObj == null) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        Long userIdNum = parseUserIdNumeric(userIdObj);
        if (userIdNum == null) {
            throw CustomException.badRequest("userId는 숫자(app_user.id)여야 합니다.", "INVALID_INPUT");
        }
        String username = appUserResolver.getUsernameById(userIdNum);
        if (username == null || username.isBlank()) {
            throw CustomException.badRequest("해당 사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
        }
        AssignUserToGroupResponse data = permissionGroupService.assignUser(id, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * DELETE /api/permission-groups/{id}/users/{userId} — remove user. §14.7. Path userId = numeric app_user.id (req 20260316).
     */
    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> unassignUser(
            @PathVariable Long id,
            @PathVariable Long userId,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        requireWriteForManagement(request);
        String username = appUserResolver.getUsernameById(userId);
        if (username == null || username.isBlank()) {
            throw CustomException.badRequest("해당 사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
        }
        permissionGroupService.unassignUser(id, username);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * GET /api/permission-groups/{id}/users — list users in group. §14.8
     */
    @GetMapping("/{id}/users")
    public ResponseEntity<ApiResponse<List<UserListItemResponse>>> listUsers(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        List<UserListItemResponse> data = permissionGroupService.listUsersInGroup(id);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
