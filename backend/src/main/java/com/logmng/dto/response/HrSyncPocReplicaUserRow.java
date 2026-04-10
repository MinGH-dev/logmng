package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Row in {@code GET /api/hr-sync/poc/user-mgmt/replica-users} {@code data.employees}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocReplicaUserRow {

    private String externalEmployeeId;
    private String employeeNumber;
    private String displayName;
    private String jobTitle;
    private String departmentKey;
    private String departmentName;
    private boolean active;
    private String snapshotId;

    public HrSyncPocReplicaUserRow() {
    }

    public HrSyncPocReplicaUserRow(
            String externalEmployeeId,
            String employeeNumber,
            String displayName,
            String jobTitle,
            String departmentKey,
            String departmentName,
            boolean active,
            String snapshotId) {
        this.externalEmployeeId = externalEmployeeId;
        this.employeeNumber = employeeNumber;
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.departmentKey = departmentKey;
        this.departmentName = departmentName;
        this.active = active;
        this.snapshotId = snapshotId;
    }

    public String getExternalEmployeeId() {
        return externalEmployeeId;
    }

    public void setExternalEmployeeId(String externalEmployeeId) {
        this.externalEmployeeId = externalEmployeeId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getDepartmentKey() {
        return departmentKey;
    }

    public void setDepartmentKey(String departmentKey) {
        this.departmentKey = departmentKey;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    @JsonProperty("isActive")
    public boolean isActive() {
        return active;
    }

    @JsonProperty("isActive")
    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }
}
