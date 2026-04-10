package com.logmng.service;

import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.dto.request.UserManagementV2DirectUserCreateRequest;
import com.logmng.util.UserManagementReadScopeContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StubUserManagementV2Service extends UserManagementV2Service {

    private final AtomicReference<Map<String, Object>> rootResult = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> childResult = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> userResult = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> optionsResult = new AtomicReference<>();

    public StubUserManagementV2Service() {
        super(null, null, null);
    }

    public void setRootResult(Map<String, Object> value) {
        rootResult.set(value);
    }

    public void setChildResult(Map<String, Object> value) {
        childResult.set(value);
    }

    public void setUserResult(Map<String, Object> value) {
        userResult.set(value);
    }

    public void setOptionsResult(Map<String, Object> value) {
        optionsResult.set(value);
    }

    @Override
    public Map<String, Object> createRootDepartment(UserManagementV2CreateDepartmentRequest body, String actorUsername, String clientIp, String userAgent,
                                                    String requestPath, UserManagementReadScopeContext scopeCtx) {
        return rootResult.get() != null ? rootResult.get() : defaultDepartment("ROOT");
    }

    @Override
    public Map<String, Object> createChildDepartment(String parentDepartmentId, UserManagementV2CreateDepartmentRequest body,
                                                     String actorUsername, String clientIp, String userAgent, String requestPath,
                                                     UserManagementReadScopeContext scopeCtx) {
        return childResult.get() != null ? childResult.get() : defaultDepartment(parentDepartmentId + "_CHILD");
    }

    @Override
    public Map<String, Object> deleteDepartment(String departmentIdRaw, UserDeleteRequest body, String actorUsername,
                                                String clientIp, String userAgent, String requestPath,
                                                UserManagementReadScopeContext scopeCtx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departmentId", departmentIdRaw != null ? departmentIdRaw.trim() : "");
        return data;
    }

    @Override
    public Map<String, Object> createDirectUser(UserManagementV2DirectUserCreateRequest body, String actorUsername, String clientIp, String userAgent,
                                                String requestPath, UserManagementReadScopeContext scopeCtx) {
        if (userResult.get() != null) {
            return userResult.get();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", 20269999L);
        data.put("employeeNumber", "20269999");
        data.put("name", "테스터");
        data.put("rank", "대리");
        data.put("departmentId", "ROOT");
        data.put("permissionGroupId", 1L);
        return data;
    }

    @Override
    public Map<String, Object> getQuickEntryOptions(String actorUsername, List<String> fields, Integer limit,
                                                      UserManagementReadScopeContext scopeCtx) {
        if (optionsResult.get() != null) {
            return optionsResult.get();
        }
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("previous", "20269999");
        field.put("recent", List.of("20269999"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeNumber", field);
        return data;
    }

    private static Map<String, Object> defaultDepartment(String code) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departmentId", code);
        data.put("name", "부서");
        data.put("code", code);
        data.put("parentDepartmentId", null);
        data.put("sortOrder", 0);
        return data;
    }
}
