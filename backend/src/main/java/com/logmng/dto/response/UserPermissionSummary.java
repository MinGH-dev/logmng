package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * User with permission groups for hierarchy node (§14.9). userId (numeric app_user.id), position, rank, permissionGroups, isSystemAdmin.
 * role is internal only, not exposed in JSON (req 20250303). Req 20260316: userId = Long.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPermissionSummary {

    private Long userId;
    private String userName;
    private String role;
    private String position;
    private String rank;
    private List<PermissionGroupSummary> permissionGroups;
    private Boolean isSystemAdmin;
    private String employeeNumber;

    public UserPermissionSummary() {
        this.permissionGroups = new ArrayList<>();
    }

    public UserPermissionSummary(Long userId, String role, List<PermissionGroupSummary> permissionGroups) {
        this(userId, null, role, null, null, permissionGroups, false, null);
    }

    public UserPermissionSummary(Long userId, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups) {
        this(userId, null, role, position, rank, permissionGroups, false, null);
    }

    public UserPermissionSummary(Long userId, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups, boolean isSystemAdmin) {
        this(userId, null, role, position, rank, permissionGroups, isSystemAdmin, null);
    }

    /**
     * Full constructor including display name (userName). userName = app_user.name when not blank, else username.
     */
    public UserPermissionSummary(Long userId, String userName, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups, boolean isSystemAdmin) {
        this(userId, userName, role, position, rank, permissionGroups, isSystemAdmin, null);
    }

    /**
     * Same as full constructor with optional {@code app_user.employee_number} (omit from JSON when null).
     */
    public UserPermissionSummary(Long userId, String userName, String role, String position, String rank, List<PermissionGroupSummary> permissionGroups, boolean isSystemAdmin, String employeeNumber) {
        this.userId = userId;
        this.userName = (userName != null && !userName.isBlank()) ? userName : (userId != null ? String.valueOf(userId) : null);
        this.role = role;
        this.position = position;
        this.rank = rank;
        this.permissionGroups = permissionGroups != null ? permissionGroups : new ArrayList<>();
        this.isSystemAdmin = isSystemAdmin;
        this.employeeNumber = (employeeNumber != null && !employeeNumber.isBlank()) ? employeeNumber.trim() : null;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = (employeeNumber != null && !employeeNumber.isBlank()) ? employeeNumber.trim() : null;
    }
}
