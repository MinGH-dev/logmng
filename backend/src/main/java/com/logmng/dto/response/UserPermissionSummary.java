package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * User with permission groups for hierarchy node (§14.9). userId, role, permissionGroups
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPermissionSummary {

    private String userId;
    private String role;
    private List<PermissionGroupSummary> permissionGroups;

    public UserPermissionSummary() {
        this.permissionGroups = new ArrayList<>();
    }

    public UserPermissionSummary(String userId, String role, List<PermissionGroupSummary> permissionGroups) {
        this.userId = userId;
        this.role = role;
        this.permissionGroups = permissionGroups != null ? permissionGroups : new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<PermissionGroupSummary> getPermissionGroups() {
        return permissionGroups;
    }

    public void setPermissionGroups(List<PermissionGroupSummary> permissionGroups) {
        this.permissionGroups = permissionGroups != null ? permissionGroups : new ArrayList<>();
    }
}
