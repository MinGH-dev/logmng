package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Row in {@code GET /api/hr-sync/poc/snapshots/{snapshotId}/employees} {@code data.employees}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocEmployeeRow {

    private String displayName;
    private String jobTitle;
    private String departmentKey;
    private String departmentName;
    private boolean active;
    private String employeeNumber;

    public HrSyncPocEmployeeRow() {
    }

    public HrSyncPocEmployeeRow(
            String displayName,
            String jobTitle,
            String departmentKey,
            String departmentName,
            boolean active,
            String employeeNumber) {
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.departmentKey = departmentKey;
        this.departmentName = departmentName;
        this.active = active;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }
}
