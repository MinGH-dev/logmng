package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 부서 트리 노드 (§12.1). code, parentCode, name, sortOrder, children
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepartmentNodeResponse {

    private String code;
    @JsonProperty("parentCode")
    private String parentCode;
    private String name;
    @JsonProperty("sortOrder")
    private Integer sortOrder;
    private List<DepartmentNodeResponse> children;

    public DepartmentNodeResponse() {
        this.children = new ArrayList<>();
    }

    public DepartmentNodeResponse(String code, String parentCode, String name, Integer sortOrder) {
        this.code = code;
        this.parentCode = parentCode;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.children = new ArrayList<>();
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

    public List<DepartmentNodeResponse> getChildren() {
        return children;
    }

    public void setChildren(List<DepartmentNodeResponse> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
