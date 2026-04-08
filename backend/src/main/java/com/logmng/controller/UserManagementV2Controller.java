package com.logmng.controller;

import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.dto.request.UserManagementV2DirectUserCreateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.UserManagementV2Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-management-v2")
public class UserManagementV2Controller {

    private static final Logger log = LoggerFactory.getLogger(UserManagementV2Controller.class);

    private final AuthService authService;
    private final UserManagementV2Service userManagementV2Service;

    public UserManagementV2Controller(AuthService authService, UserManagementV2Service userManagementV2Service) {
        this.authService = authService;
        this.userManagementV2Service = userManagementV2Service;
    }

    private LoginResponse requireUserManagementView(HttpServletRequest request) {
        if (!authService.checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!authService.canAccessUserManagementView(request)) {
            log.info("UserManagement v2 access denied: no user-management view");
            throw CustomException.forbidden("해당 화면에 대한 접근 권한이 없습니다.", "FORBIDDEN");
        }
        LoginResponse current = authService.getCurrentUserInfo(request);
        if (current == null || current.getUsername() == null || current.getUsername().isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        return current;
    }

    private LoginResponse requireUserManagementWrite(HttpServletRequest request) {
        LoginResponse current = requireUserManagementView(request);
        if (!authService.hasWriteForManagementScreens(request)) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        return current;
    }

    @PostMapping("/departments/root")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRootDepartment(
            @RequestBody UserManagementV2CreateDepartmentRequest body,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementWrite(request);
        Map<String, Object> data = userManagementV2Service.createRootDepartment(
                body, current.getUsername().trim(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * Path-variable style (parent code in URL). Codes containing reserved path characters ({@code /},
     * some proxies) may not reach the controller; use {@link #createChildDepartmentWithParentInBody} instead.
     */
    @PostMapping("/departments/{parentDepartmentId}/children")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChildDepartment(
            @PathVariable String parentDepartmentId,
            @RequestBody UserManagementV2CreateDepartmentRequest body,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementWrite(request);
        Map<String, Object> data = userManagementV2Service.createChildDepartment(
                parentDepartmentId, body, current.getUsername().trim(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * Same as {@link #createChildDepartment} but parent code is in the JSON body ({@code parentDepartmentId}) so
     * values containing {@code /} are supported without query params or path-variable matching ambiguity.
     */
    @PostMapping("/departments/children")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChildDepartmentWithParentInBody(
            @RequestBody UserManagementV2CreateDepartmentRequest body,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementWrite(request);
        String parentTrim = body != null && body.getParentDepartmentId() != null
                ? body.getParentDepartmentId().trim()
                : "";
        if (parentTrim.isEmpty()) {
            throw CustomException.badRequest("parentDepartmentId는 필수이며 공백일 수 없습니다.", "INVALID_INPUT");
        }
        Map<String, Object> data = userManagementV2Service.createChildDepartment(
                parentTrim, body, current.getUsername().trim(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    @PostMapping("/users/direct")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDirectUser(
            @RequestBody UserManagementV2DirectUserCreateRequest body,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementWrite(request);
        Map<String, Object> data = userManagementV2Service.createDirectUser(
                body, current.getUsername().trim(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * DELETE /api/user-management-v2/departments/{departmentId} — hard delete; {@code departmentId} is {@code department.code}.
     * Body {@link UserDeleteRequest#changeReason} required (same rules as user soft-delete).
     */
    @DeleteMapping("/departments/{departmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteDepartment(
            @PathVariable String departmentId,
            @RequestBody UserDeleteRequest body,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementWrite(request);
        Map<String, Object> data = userManagementV2Service.deleteDepartment(
                departmentId,
                body,
                current.getUsername().trim(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getRequestURI());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/quick-entry/options")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuickEntryOptions(
            @RequestParam(required = false) String fields,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        LoginResponse current = requireUserManagementView(request);
        List<String> parsedFields = null;
        if (fields != null && !fields.trim().isEmpty()) {
            parsedFields = Arrays.stream(fields.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        Map<String, Object> data = userManagementV2Service.getQuickEntryOptions(current.getUsername().trim(), parsedFields, limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
