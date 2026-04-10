package com.logmng.controller;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.FilterOptionsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared filter-option APIs used by end-user screens.
 */
@RestController
@RequestMapping("/api/filter-options")
public class FilterOptionsController {

    private static final Set<String> DEPARTMENT_OPTION_SCREENS = Collections.unmodifiableSet(
            Arrays.asList(
                    ScreenConstants.ACTIVITY_LOG,
                    ScreenConstants.STATISTICS,
                    ScreenConstants.SEARCH_HISTORY,
                    ScreenConstants.PENDING_APPROVALS
            ).stream().collect(Collectors.toSet())
    );

    private final FilterOptionsService filterOptionsService;
    private final AuthService authService;

    public FilterOptionsController(FilterOptionsService filterOptionsService, AuthService authService) {
        this.filterOptionsService = filterOptionsService;
        this.authService = authService;
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<String>>> getDepartments(
            @RequestParam("screen") String screenId,
            HttpServletRequest request) {
        String normalizedScreenId = screenId != null ? screenId.trim() : null;
        if (normalizedScreenId == null || !DEPARTMENT_OPTION_SCREENS.contains(normalizedScreenId)) {
            throw CustomException.badRequest(
                    "`screen`은 activity-log, statistics, search-history, pending-approvals 중 하나여야 합니다.",
                    "INVALID_SCREEN_ID"
            );
        }

        LoginResponse userInfo = authService.requireScreenAccess(request, normalizedScreenId);
        List<String> data = filterOptionsService.getDepartmentOptions(normalizedScreenId, userInfo);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
