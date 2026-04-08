package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Item in {@code GET /api/hr-sync/poc/snapshots} {@code data.snapshots}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocSnapshotItem {

    private String snapshotId;
    private String label;
    private int employeeCount;
    private String maxImportedAt;

    public HrSyncPocSnapshotItem() {
    }

    public HrSyncPocSnapshotItem(String snapshotId, String label, int employeeCount, String maxImportedAt) {
        this.snapshotId = snapshotId;
        this.label = label;
        this.employeeCount = employeeCount;
        this.maxImportedAt = maxImportedAt;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(int employeeCount) {
        this.employeeCount = employeeCount;
    }

    public String getMaxImportedAt() {
        return maxImportedAt;
    }

    public void setMaxImportedAt(String maxImportedAt) {
        this.maxImportedAt = maxImportedAt;
    }
}
