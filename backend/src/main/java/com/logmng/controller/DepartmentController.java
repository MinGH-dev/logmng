package com.logmng.controller;

import com.logmng.dto.request.AddApproverRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.DepartmentNodeResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.DepartmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private static final int MAX_USER_ID_LENGTH = 100;

    private final DepartmentService departmentService;
    private final DecryptApproverService decryptApproverService;

    public DepartmentController(DepartmentService departmentService, DecryptApproverService decryptApproverService) {
        this.departmentService = departmentService;
        this.decryptApproverService = decryptApproverService;
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

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        String trimmed = userId.trim();
        if (trimmed.length() > MAX_USER_ID_LENGTH || hasControlOrHighChars(trimmed)) {
            throw CustomException.badRequest("유효하지 않은 userId입니다.", "INVALID_INPUT");
        }
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

    private void requireAdmin(HttpServletRequest request) {
        String userId = getUserId(request);
        if (userId == null || userId.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        String role = getRole(request);
        if (!decryptApproverService.isAdmin(role)) {
            log.info("부서/결재자 API 접근 거부: role={}", role != null ? role : "null");
            throw CustomException.forbidden("관리자만 부서 및 부서별 결재자를 관리할 수 있습니다.", "FORBIDDEN");
        }
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

    /**
     * GET /api/departments/{code}/approvers — 해당 부서 결재자 목록. §12.2
     */
    @GetMapping("/{code}/approvers")
    public ResponseEntity<ApiResponse<List<UserListItemResponse>>> listApprovers(
            @PathVariable String code,
            HttpServletRequest request) {
        requireAdmin(request);
        validateDepartmentCode(code);
        List<UserListItemResponse> data = decryptApproverService.listApproversByDepartment(code);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * POST /api/departments/{code}/approvers — 부서별 결재자 추가. §12.3
     */
    @PostMapping("/{code}/approvers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addApprover(
            @PathVariable String code,
            @Valid @RequestBody AddApproverRequest body,
            HttpServletRequest request) {
        requireAdmin(request);
        validateDepartmentCode(code);
        validateUserId(body.getUserId());
        UserListItemResponse result = decryptApproverService.addApproverForDepartment(code, body.getUserId());
        Map<String, Object> data = Map.of(
                "userId", result.getUserId(),
                "departmentCode", code,
                "isApprover", true
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * DELETE /api/departments/{code}/approvers/{userId} — 부서별 결재자 제거. §12.4
     */
    @DeleteMapping("/{code}/approvers/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeApprover(
            @PathVariable String code,
            @PathVariable String userId,
            HttpServletRequest request) {
        requireAdmin(request);
        validateDepartmentCode(code);
        validateUserId(userId);
        UserListItemResponse result = decryptApproverService.removeApproverForDepartment(code, userId);
        Map<String, Object> data = Map.of(
                "userId", result.getUserId(),
                "departmentCode", code,
                "isApprover", false
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
