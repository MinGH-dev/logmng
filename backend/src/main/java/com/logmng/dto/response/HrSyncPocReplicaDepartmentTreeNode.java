package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Node in {@code GET /api/hr-sync/poc/user-mgmt/replica-departments/tree} {@code data.roots} (nested).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocReplicaDepartmentTreeNode {

    private String departmentKey;
    private String parentDepartmentKey;
    private String name;
    private int sortOrder;
    private List<HrSyncPocReplicaDepartmentTreeNode> children = new ArrayList<>();

    public HrSyncPocReplicaDepartmentTreeNode() {
    }

    public HrSyncPocReplicaDepartmentTreeNode(
            String departmentKey,
            String parentDepartmentKey,
            String name,
            int sortOrder,
            List<HrSyncPocReplicaDepartmentTreeNode> children) {
        this.departmentKey = departmentKey;
        this.parentDepartmentKey = parentDepartmentKey;
        this.name = name;
        this.sortOrder = sortOrder;
        this.children = children != null ? children : new ArrayList<>();
    }

    public String getDepartmentKey() {
        return departmentKey;
    }

    public void setDepartmentKey(String departmentKey) {
        this.departmentKey = departmentKey;
    }

    public String getParentDepartmentKey() {
        return parentDepartmentKey;
    }

    public void setParentDepartmentKey(String parentDepartmentKey) {
        this.parentDepartmentKey = parentDepartmentKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<HrSyncPocReplicaDepartmentTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<HrSyncPocReplicaDepartmentTreeNode> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
