package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

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

    public UserController(DecryptApproverService decryptApproverService, AuthService authService) {
        this.decryptApproverService = decryptApproverService;
        this.authService = authService;
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
     * GET /api/users — 관리자 전용. 사용자 목록(departmentCode, isApprover, isSystemAdmin).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserListItemResponse>>> listUsers(HttpServletRequest request) {
        requireUserManagementAccess(request);
        List<UserListItemResponse> data = decryptApproverService.listUsers();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * PUT /api/users/{userId} — 410 Gone. 역할 변경 엔드포인트 제거됨 (req 20250303).
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> updateUserRole(
            @PathVariable String userId,
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
