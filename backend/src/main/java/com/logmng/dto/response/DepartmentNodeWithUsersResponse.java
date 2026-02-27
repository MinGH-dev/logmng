package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Department tree node with users and their permission groups (§14.9).
 * code, parentCode, name, sortOrder, children, users
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepartmentNodeWithUsersResponse {

    private String code;
    @JsonProperty("parentCode")
    private String parentCode;
    private String name;
    @JsonProperty("sortOrder")
    private Integer sortOrder;
    private List<DepartmentNodeWithUsersResponse> children;
    private List<UserPermissionSummary> users;

    public DepartmentNodeWithUsersResponse() {
        this.children = new ArrayList<>();
        this.users = new ArrayList<>();
    }

    public DepartmentNodeWithUsersResponse(String code, String parentCode, String name, Integer sortOrder) {
        this.code = code;
        this.parentCode = parentCode;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.children = new ArrayList<>();
        this.users = new ArrayList<>();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<DepartmentNodeWithUsersResponse> getChildren() {
        return children;
    }

    public void setChildren(List<DepartmentNodeWithUsersResponse> children) {
        this.children = children != null ? children : new ArrayList<>();
    }

    public List<UserPermissionSummary> getUsers() {
        return users;
    }

    public void setUsers(List<UserPermissionSummary> users) {
        this.users = users != null ? users : new ArrayList<>();
    }
}
