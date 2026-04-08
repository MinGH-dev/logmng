package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserManagementV2DirectUserCreateRequest {

    @JsonProperty("departmentId")
    private String departmentId;

    @JsonProperty("employeeNumber")
    private String employeeNumber;

    @JsonProperty("name")
    private String name;

    @JsonProperty("rank")
    private String rank;

    @JsonProperty("permissionGroupId")
    private Long permissionGroupId;

    @JsonProperty("changeReason")
    private String changeReason;

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public Long getPermissionGroupId() {
        return permissionGroupId;
    }

    public void setPermissionGroupId(Long permissionGroupId) {
        this.permissionGroupId = permissionGroupId;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
