package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.DepartmentNodeResponse;
import com.logmng.dto.response.DepartmentNodeWithUsersResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.DepartmentService;
import com.logmng.service.UserPermissionHierarchyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 부서 계층 및 부서별 결재자. §12. 관리자 전용.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private static final Logger log = LoggerFactory.getLogger(DepartmentController.class);
    private static final int MAX_DEPARTMENT_CODE_LENGTH = 50;

    private final DepartmentService departmentService;
    private final DecryptApproverService decryptApproverService;
    private final UserPermissionHierarchyService userPermissionHierarchyService;

    public DepartmentController(DepartmentService departmentService, DecryptApproverService decryptApproverService,
                                UserPermissionHierarchyService userPermissionHierarchyService) {
        this.departmentService = departmentService;
        this.decryptApproverService = decryptApproverService;
        this.userPermissionHierarchyService = userPermissionHierarchyService;
    }

    private static boolean hasControlOrHighChars(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c == 127 || c > 127) return true;
        }
        return false;
    }

    private void validateDepartmentCode(String code) {
        if (code == null || code.isBlank()) {
            throw CustomException.badRequest("부서 코드는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        String trimmed = code.trim();
        if (trimmed.length() > MAX_DEPARTMENT_CODE_LENGTH || hasControlOrHighChars(trimmed)) {
            throw CustomException.badRequest("유효하지 않은 부서 코드입니다.", "INVALID_INPUT");
        }
    }

    private static String getUserId(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute("userId");
        return v != null ? v.toString() : null;
    }

    private static boolean isSystemAdmin(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object v = session.getAttribute("isSystemAdmin");
        return Boolean.TRUE.equals(v);
    }

    private void requireAdmin(HttpServletRequest request) {
        String userId = getUserId(request);
        if (userId == null || userId.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!decryptApproverService.isAdmin(isSystemAdmin(request))) {
            log.info("부서/결재자 API 접근 거부: isSystemAdmin=false");
            throw CustomException.forbidden("관리자만 부서 및 부서별 결재자를 관리할 수 있습니다.", "FORBIDDEN");
        }
    }

    /**
     * GET /api/departments/user-permission-hierarchy — 부서별 사용자·권한 그룹 계층. §14.9
     */
    @GetMapping("/user-permission-hierarchy")
    public ResponseEntity<ApiResponse<?>> userPermissionHierarchy(
            @RequestParam(defaultValue = "tree") String format,
            HttpServletRequest request) {
        requireAdmin(request);
        if ("flat".equalsIgnoreCase(format)) {
            List<DepartmentNodeWithUsersResponse> data = userPermissionHierarchyService.getHierarchyFlat();
            return ResponseEntity.ok(ApiResponse.success(data));
        }
        List<DepartmentNodeWithUsersResponse> data = userPermissionHierarchyService.getHierarchyTree();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * GET /api/departments — 부서 트리 또는 평면 목록. §12.1
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(
            @RequestParam(defaultValue = "tree") String format,
            HttpServletRequest request) {
        requireAdmin(request);
        if ("flat".equalsIgnoreCase(format)) {
            List<Map<String, Object>> data = departmentService.listFlat();
            return ResponseEntity.ok(ApiResponse.success(data));
        }
        List<DepartmentNodeResponse> data = departmentService.listTree();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
