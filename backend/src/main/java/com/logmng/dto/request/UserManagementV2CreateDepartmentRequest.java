package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserManagementV2CreateDepartmentRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("code")
    private String code;

    @JsonProperty("sortOrder")
    private Integer sortOrder;

    @JsonProperty("changeReason")
    private String changeReason;

    /** Required for POST /departments/children; ignored for POST /departments/root. */
    @JsonProperty("parentDepartmentId")
    private String parentDepartmentId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getParentDepartmentId() {
        return parentDepartmentId;
    }

    public void setParentDepartmentId(String parentDepartmentId) {
        this.parentDepartmentId = parentDepartmentId;
    }
}
