package com.logmng.controller;

import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.DepartmentService;
import com.logmng.util.UserManagementReadScopeContext;
import com.logmng.util.UserManagementReadScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import javax.sql.DataSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 관리 (관리자 전용). §7
 * GET /api/users, PUT /api/users/{userId} (410 Gone — role update deprecated)
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final DecryptApproverService decryptApproverService;
    private final AuthService authService;
    private final DepartmentService departmentService;
    private final DataSource dataSource;

    public UserController(DecryptApproverService decryptApproverService, AuthService authService,
                          DepartmentService departmentService, DataSource dataSource) {
        this.decryptApproverService = decryptApproverService;
        this.authService = authService;
        this.departmentService = departmentService;
        this.dataSource = dataSource;
    }

    /** Allows isSystemAdmin OR allowedScreenIds contains user-management or user-permission-hierarchy. Per spec §4.3. */
    private void requireUserManagementAccess(HttpServletRequest request) {
        if (!authService.checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!authService.canAccessUserManagementView(request)) {
            log.info("User API access denied: no user-management or user-permission-hierarchy");
            throw CustomException.forbidden("관리자만 사용자 목록을 조회할 수 있습니다.", "FORBIDDEN");
        }
    }

    /**
     * GET /api/users — 관리자 전용. 사용자 목록(departmentCode, isSystemAdmin). Approver capability not in payload (req 20260323).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserListItemResponse>>> listUsers(HttpServletRequest request) {
        requireUserManagementAccess(request);
        LoginResponse current = authService.getCurrentUserInfo(request);
        UserManagementReadScopeContext ctx = UserManagementReadScopeResolver.resolve(request, current, dataSource, departmentService);
        List<UserListItemResponse> data = decryptApproverService.listUsers(ctx.getAllowedNumericUserIds());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * DELETE /api/users/{userId} — soft delete with required {@code changeReason}. §7.3, req 20260407.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> deleteUser(
            @PathVariable Long userId,
            @RequestBody UserDeleteRequest body,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        LoginResponse current = authService.getCurrentUserInfo(request);
        if (current == null || current.getUsername() == null || current.getUsername().isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        UserManagementReadScopeContext scopeCtx = UserManagementReadScopeResolver.resolve(request, current, dataSource, departmentService);
        if (scopeCtx.restrictsUserIds() && !scopeCtx.getAllowedNumericUserIds().contains(userId)) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        decryptApproverService.softDeleteUserById(
                userId,
                body != null ? body.getChangeReason() : null,
                current.getUsername().trim(),
                request.getRemoteAddr());
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("userId", userId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * PUT /api/users/{userId} — 410 Gone. 역할 변경 엔드포인트 제거됨 (req 20250303). Path userId = numeric app_user.id (req 20260316).
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> updateUserRole(
            @PathVariable Long userId,
            HttpServletRequest request) {
        requireUserManagementAccess(request);
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.failure("역할 변경 API는 제거되었습니다. 권한은 권한 그룹으로 관리됩니다.", "ENDPOINT_REMOVED"));
    }

    /**
     * POST /api/users/approvers — removed (req 20250227). Return 410 Gone for API consistency.
     */
    @PostMapping("/approvers")
    public ResponseEntity<?> addApproverRemoved() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    /**
     * DELETE /api/users/approvers — removed (req 20250227). Return 410 Gone for API consistency.
     */
    @DeleteMapping("/approvers")
    public ResponseEntity<?> removeApproverRemoved() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
