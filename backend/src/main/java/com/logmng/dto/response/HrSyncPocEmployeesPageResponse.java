package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code data} for {@code GET /api/hr-sync/poc/snapshots/{snapshotId}/employees}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocEmployeesPageResponse {

    private String snapshotId;
    private List<HrSyncPocEmployeeRow> employees;
    private PaginationResponse pagination;

    public HrSyncPocEmployeesPageResponse() {
    }

    public HrSyncPocEmployeesPageResponse(
            String snapshotId,
            List<HrSyncPocEmployeeRow> employees,
            PaginationResponse pagination) {
        this.snapshotId = snapshotId;
        this.employees = employees;
        this.pagination = pagination;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public List<HrSyncPocEmployeeRow> getEmployees() {
        return employees;
    }

    public void setEmployees(List<HrSyncPocEmployeeRow> employees) {
        this.employees = employees;
    }

    public PaginationResponse getPagination() {
        return pagination;
    }

    public void setPagination(PaginationResponse pagination) {
        this.pagination = pagination;
    }
}
