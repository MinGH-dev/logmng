package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 사용자 목록 항목 (§7.1): userId, username, role, departmentCode, position, rank, isApprover
 */
public class UserListItemResponse {

    private String userId;
    private String username;
    private String role;
    private String departmentCode;
    private String position;
    private String rank;
    @JsonProperty("isApprover")
    private boolean approver;

    public UserListItemResponse() {
    }

    public UserListItemResponse(String userId, String role, String departmentCode, boolean approver) {
        this(userId, role, departmentCode, approver, null, null);
    }

    public UserListItemResponse(String userId, String role, String departmentCode, boolean approver, String position) {
        this(userId, role, departmentCode, approver, position, null);
    }

    public UserListItemResponse(String userId, String role, String departmentCode, boolean approver, String position, String rank) {
        this.userId = userId;
        this.username = userId;
        this.role = role;
        this.departmentCode = departmentCode;
        this.approver = approver;
        this.position = position;
        this.rank = rank;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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
}
