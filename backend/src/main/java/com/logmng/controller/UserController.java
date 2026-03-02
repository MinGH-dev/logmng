package com.logmng.controller;

import com.logmng.dto.request.UpdateUserRoleRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.DecryptApproverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 사용자 관리 (관리자 전용). §7
 * GET /api/users, PUT /api/users/{userId}
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final DecryptApproverService decryptApproverService;

    public UserController(DecryptApproverService decryptApproverService) {
        this.decryptApproverService = decryptApproverService;
    }

    private static String getUserId(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("userId");
        return v != null ? v.toString() : null;
    }

    private static String getRole(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("role");
        return v != null ? v.toString() : null;
    }

    /**
     * GET /api/users — 관리자 전용. 사용자 목록(role, departmentCode, isApprover).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserListItemResponse>>> listUsers(HttpServletRequest request) {
        String userId = getUserId(request);
        if (userId == null || userId.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        String role = getRole(request);
        if (!decryptApproverService.isAdmin(role)) {
            throw CustomException.forbidden("관리자만 사용자 목록을 조회할 수 있습니다.", "FORBIDDEN");
        }
        List<UserListItemResponse> data = decryptApproverService.listUsers();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * PUT /api/users/{userId} — 관리자 전용. 사용자 역할 변경. §7.4
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserListItemResponse>> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest body,
            HttpServletRequest request) {
        String callerUserId = getUserId(request);
        if (callerUserId == null || callerUserId.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        String role = getRole(request);
        if (!decryptApproverService.isAdmin(role)) {
            throw CustomException.forbidden("관리자만 사용자 역할을 변경할 수 있습니다.", "FORBIDDEN");
        }
        UserListItemResponse result = decryptApproverService.updateUserRole(
                callerUserId, userId, body.getRole());
        return ResponseEntity.ok(ApiResponse.success(result));
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
