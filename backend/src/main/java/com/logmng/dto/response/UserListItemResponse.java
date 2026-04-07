package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 사용자 목록 항목 (§7.1): userId (numeric app_user.id), username, departmentCode, position, rank, isApprover, isSystemAdmin.
 * role is internal only, not exposed in JSON (req 20250303). Req 20260316: userId = Long.
 */
public class UserListItemResponse {

    private Long userId;
    private String username;
    private String role;
    private String departmentCode;
    private String position;
    private String rank;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String employeeNumber;
    @JsonProperty("isApprover")
    private boolean approver;
    @JsonProperty("isSystemAdmin")
    private boolean systemAdmin;

    public UserListItemResponse() {
    }

    public UserListItemResponse(Long userId, String username, String role, String departmentCode, boolean approver) {
        this(userId, username, role, departmentCode, approver, null, null, false);
    }

    public UserListItemResponse(Long userId, String username, String role, String departmentCode, boolean approver, String position) {
        this(userId, username, role, departmentCode, approver, position, null, false);
    }

    public UserListItemResponse(Long userId, String username, String role, String departmentCode, boolean approver, String position, String rank) {
        this(userId, username, role, departmentCode, approver, position, rank, false);
    }

    public UserListItemResponse(Long userId, String username, String role, String departmentCode, boolean approver, String position, String rank, boolean isSystemAdmin) {
        this(userId, username, role, departmentCode, approver, position, rank, isSystemAdmin, null);
    }

    public UserListItemResponse(Long userId, String username, String role, String departmentCode, boolean approver, String position, String rank, boolean isSystemAdmin, String employeeNumber) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.departmentCode = departmentCode;
        this.approver = approver;
        this.position = position;
        this.rank = rank;
        this.systemAdmin = isSystemAdmin;
        this.employeeNumber = (employeeNumber != null && !employeeNumber.isBlank()) ? employeeNumber.trim() : null;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
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

    public boolean isApprover() {
        return approver;
    }

    public void setApprover(boolean approver) {
        this.approver = approver;
    }

    public boolean isSystemAdmin() {
        return systemAdmin;
    }

    public void setSystemAdmin(boolean systemAdmin) {
        this.systemAdmin = systemAdmin;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = (employeeNumber != null && !employeeNumber.isBlank()) ? employeeNumber.trim() : null;
    }
}
