package com.logmng.dto.response;

import java.util.ArrayList;
import java.util.List;

/** {@code data} for {@code GET /api/hr-sync/poc/user-mgmt/replica-departments/tree}. */
public class HrSyncPocReplicaDepartmentTreeResponse {

    private String sourceSystem;
    private List<HrSyncPocReplicaDepartmentTreeNode> roots = new ArrayList<>();

    public HrSyncPocReplicaDepartmentTreeResponse() {
    }

    public HrSyncPocReplicaDepartmentTreeResponse(String sourceSystem, List<HrSyncPocReplicaDepartmentTreeNode> roots) {
        this.sourceSystem = sourceSystem;
        this.roots = roots != null ? roots : new ArrayList<>();
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public List<HrSyncPocReplicaDepartmentTreeNode> getRoots() {
        return roots;
    }

    public void setRoots(List<HrSyncPocReplicaDepartmentTreeNode> roots) {
        this.roots = roots != null ? roots : new ArrayList<>();
    }
}
