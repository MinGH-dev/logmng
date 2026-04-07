package com.logmng.controller;

import com.logmng.dto.request.ExternalDepartmentSearchRequest;
import com.logmng.dto.request.ExternalEmployeeSearchRequest;
import com.logmng.dto.request.ProvisionFromExternalEmployeeRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.ExternalDepartmentSearchResult;
import com.logmng.dto.response.ExternalEmployeeSearchResult;
import com.logmng.dto.response.ProvisionUserResultResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.ProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Admin provisioning APIs (spec: external-identity-auth.spec.yaml §4).
 */
@RestController
@RequestMapping("/api/provisioning")
public class ProvisioningController {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningController.class);

    private final ProvisioningService provisioningService;
    private final AuthService authService;

    public ProvisioningController(ProvisioningService provisioningService, AuthService authService) {
        this.provisioningService = provisioningService;
        this.authService = authService;
    }

    private void requireProvisioningAccess(HttpServletRequest request) {
        if (!authService.checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (!authService.canAccessUserManagementView(request)) {
            log.info("Provisioning API access denied");
            throw CustomException.forbidden("관리자만 프로비저닝 API를 사용할 수 있습니다.", "FORBIDDEN");
        }
    }

    @PostMapping("/external-employees/search")
    public ResponseEntity<ApiResponse<ExternalEmployeeSearchResult>> searchEmployees(
            @RequestBody ExternalEmployeeSearchRequest body,
            HttpServletRequest request) {
        requireProvisioningAccess(request);
        ExternalEmployeeSearchResult data = provisioningService.searchExternalEmployees(body);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/external-departments/search")
    public ResponseEntity<ApiResponse<ExternalDepartmentSearchResult>> searchDepartments(
            @RequestBody ExternalDepartmentSearchRequest body,
            HttpServletRequest request) {
        requireProvisioningAccess(request);
        ExternalDepartmentSearchResult data = provisioningService.searchExternalDepartments(body);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/users/from-external-employee")
    public ResponseEntity<ApiResponse<ProvisionUserResultResponse>> provisionUser(
            @RequestBody ProvisionFromExternalEmployeeRequest body,
            HttpServletRequest request) {
        requireProvisioningAccess(request);
        ProvisionUserResultResponse data = provisioningService.provisionFromExternalEmployee(body);
        return ResponseEntity.ok(ApiResponse.success(data, "사용자가 등록되었습니다."));
    }
}
