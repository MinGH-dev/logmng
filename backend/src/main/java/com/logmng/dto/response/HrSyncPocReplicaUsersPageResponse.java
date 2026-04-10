package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/** {@code data} for {@code GET /api/hr-sync/poc/user-mgmt/replica-users}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocReplicaUsersPageResponse {

    private String snapshotId;
    private String departmentKey;
    private String sourceSystem;
    private List<HrSyncPocReplicaUserRow> employees = new ArrayList<>();
    private PaginationResponse pagination;

    public HrSyncPocReplicaUsersPageResponse() {
    }

    public HrSyncPocReplicaUsersPageResponse(
            String snapshotId,
            String departmentKey,
            String sourceSystem,
            List<HrSyncPocReplicaUserRow> employees,
            PaginationResponse pagination) {
        this.snapshotId = snapshotId;
        this.departmentKey = departmentKey;
        this.sourceSystem = sourceSystem;
        this.employees = employees != null ? employees : new ArrayList<>();
        this.pagination = pagination;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getDepartmentKey() {
        return departmentKey;
    }

    public void setDepartmentKey(String departmentKey) {
        this.departmentKey = departmentKey;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public List<HrSyncPocReplicaUserRow> getEmployees() {
        return employees;
    }

    public void setEmployees(List<HrSyncPocReplicaUserRow> employees) {
        this.employees = employees != null ? employees : new ArrayList<>();
    }

    public PaginationResponse getPagination() {
        return pagination;
    }

    public void setPagination(PaginationResponse pagination) {
        this.pagination = pagination;
    }
}
