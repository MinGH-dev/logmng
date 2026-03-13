package com.logmng.service;

import com.logmng.dto.response.LoginResponse;
import com.logmng.util.ScopeHelper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared filter-option service for scope-aware select options.
 */
@Service
public class FilterOptionsService {

    private final DepartmentService departmentService;

    public FilterOptionsService(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * Returns department options for scope-aware user-context filters.
     * The response never includes the local "All" option; frontend adds it locally.
     */
    public List<String> getDepartmentOptions(String screenId, LoginResponse userInfo) {
        if (userInfo == null) {
            throw new IllegalArgumentException("userInfo is required");
        }

        Map<String, String> screenScopes = userInfo.getScreenScopes() != null
                ? userInfo.getScreenScopes()
                : Collections.emptyMap();
        String scope = ScopeHelper.resolveScope(
                screenId,
                Boolean.TRUE.equals(userInfo.getIsSystemAdmin()),
                screenScopes);

        if ("self".equals(scope)) {
            return Collections.emptyList();
        }
        if ("team".equals(scope)) {
            String departmentName = departmentService.findCurrentDepartmentNameByUsername(userInfo.getUsername());
            return departmentName == null || departmentName.isBlank()
                    ? Collections.emptyList()
                    : Collections.singletonList(departmentName);
        }
        return departmentService.listCurrentDepartmentNames();
    }
}
