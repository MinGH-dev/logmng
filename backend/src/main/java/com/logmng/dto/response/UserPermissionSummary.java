package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * User with permission groups for hierarchy node (§14.9). userId, position, rank, permissionGroups, isSystemAdmin
 * role is internal only, not exposed in JSON (req 20250303).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPermissionSummary {

    private String userId;
    private String role;
    private String position;
    private String rank;
    private List<PermissionGroupSummary> permissionGroups;
    private Boolean isSystemAdmin;

    public UserPermissionSummary() {
        this.permissionGroups = new ArrayList<>();
    }

    public UserPermissionSummary(String userId, String role, List<PermissionGroupSummary> permissionGroups) {
        this(userId, role, null, null, permissionGroups, false);
    }

    public UserPermissionSummary(String userId, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups) {
        this(userId, role, position, rank, permissionGroups, false);
    }

    public UserPermissionSummary(String userId, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups, boolean isSystemAdmin) {
        this.userId = userId;
        this.role = role;
        this.position = position;
        this.rank = rank;
        this.permissionGroups = permissionGroups != null ? permissionGroups : new ArrayList<>();
        this.isSystemAdmin = isSystemAdmin;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @JsonIgnore
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public List<PermissionGroupSummary> getPermissionGroups() {
        return permissionGroups;
    }

    public void setPermissionGroups(List<PermissionGroupSummary> permissionGroups) {
        this.permissionGroups = permissionGroups != null ? permissionGroups : new ArrayList<>();
    }

    public Boolean getIsSystemAdmin() {
        return isSystemAdmin;
    }

    public void setIsSystemAdmin(Boolean isSystemAdmin) {
        this.isSystemAdmin = isSystemAdmin;
    }
}
