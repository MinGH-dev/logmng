package com.logmng.controller;

import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.PermissionGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public PermissionGroupController(PermissionGroupService permissionGroupService,
                                     AuthService authService) {
        this.permissionGroupService = permissionGroupService;
        this.authService = authService;
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

    /**
     * GET /api/permission-groups — list all. §14.1
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionGroupResponse>>> list(HttpServletRequest request) {
        requireUserManagementAccess(request);
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
        PermissionGroupResponse data = permissionGroupService.update(id, body);
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
        permissionGroupService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * POST /api/permission-groups/{id}/users — assign user. §14.6
     */
    @PostMapping("/{id}/users")
    public ResponseEntity<ApiResponse<AssignUserToGroupResponse>> assignUser(
            @PathVariable Long id,
            @Valid @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        String userId = body != null ? body.get("userId") : null;
        if (userId == null || userId.isBlank()) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        AssignUserToGroupResponse data = permissionGroupService.assignUser(id, userId.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * DELETE /api/permission-groups/{id}/users/{userId} — remove user. §14.7
     */
    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> unassignUser(
            @PathVariable Long id,
            @PathVariable String userId,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        permissionGroupService.unassignUser(id, userId);
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
