package com.logmng.dto.response;

public class ExternalEmployeeItemResponse {

    private String externalEmployeeId;
    private String sourceSystem;
    private String employeeNumber;
    private String displayName;
    private String externalDepartmentId;
    private String jobTitle;
    /** Display name from joined ext_department.name when present. */
    private String departmentName;
    /** True when {@code app_user_external_identity} has a row for this external key. */
    private boolean provisioned;
    /** {@code app_user.username} when provisioned; otherwise null. */
    private String provisionedUsername;
    /** {@code app_user.id} when provisioned; otherwise null. */
    private Long provisionedAppUserId;

    public ExternalEmployeeItemResponse() {
    }

    public ExternalEmployeeItemResponse(String externalEmployeeId, String sourceSystem, String employeeNumber,
                                        String displayName, String externalDepartmentId, String jobTitle) {
        this(externalEmployeeId, sourceSystem, employeeNumber, displayName, externalDepartmentId, jobTitle, null);
    }

    public ExternalEmployeeItemResponse(String externalEmployeeId, String sourceSystem, String employeeNumber,
                                        String displayName, String externalDepartmentId, String jobTitle,
                                        String departmentName) {
        this(externalEmployeeId, sourceSystem, employeeNumber, displayName, externalDepartmentId, jobTitle,
                departmentName, false, null, null);
    }

    public ExternalEmployeeItemResponse(String externalEmployeeId, String sourceSystem, String employeeNumber,
                                        String displayName, String externalDepartmentId, String jobTitle,
                                        String departmentName, boolean provisioned, String provisionedUsername,
                                        Long provisionedAppUserId) {
        this.externalEmployeeId = externalEmployeeId;
        this.sourceSystem = sourceSystem;
        this.employeeNumber = employeeNumber;
        this.displayName = displayName;
        this.externalDepartmentId = externalDepartmentId;
        this.jobTitle = jobTitle;
        this.departmentName = departmentName;
        this.provisioned = provisioned;
        this.provisionedUsername = provisionedUsername;
        this.provisionedAppUserId = provisionedAppUserId;
    }

    public String getExternalEmployeeId() {
        return externalEmployeeId;
    }

    public void setExternalEmployeeId(String externalEmployeeId) {
        this.externalEmployeeId = externalEmployeeId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
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

    public String getExternalDepartmentId() {
        return externalDepartmentId;
    }

    public void setExternalDepartmentId(String externalDepartmentId) {
        this.externalDepartmentId = externalDepartmentId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public boolean isProvisioned() {
        return provisioned;
    }

    public void setProvisioned(boolean provisioned) {
        this.provisioned = provisioned;
    }

    public String getProvisionedUsername() {
        return provisionedUsername;
    }

    public void setProvisionedUsername(String provisionedUsername) {
        this.provisionedUsername = provisionedUsername;
    }

    public Long getProvisionedAppUserId() {
        return provisionedAppUserId;
    }

    public void setProvisionedAppUserId(Long provisionedAppUserId) {
        this.provisionedAppUserId = provisionedAppUserId;
    }
}
