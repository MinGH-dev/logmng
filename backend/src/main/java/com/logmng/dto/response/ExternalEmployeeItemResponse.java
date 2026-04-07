package com.logmng.dto.response;

public class ExternalEmployeeItemResponse {

    private String externalEmployeeId;
    private String sourceSystem;
    private String employeeNumber;
    private String displayName;
    private String externalDepartmentId;
    private String jobTitle;

    public ExternalEmployeeItemResponse() {
    }

    public ExternalEmployeeItemResponse(String externalEmployeeId, String sourceSystem, String employeeNumber,
                                        String displayName, String externalDepartmentId, String jobTitle) {
        this.externalEmployeeId = externalEmployeeId;
        this.sourceSystem = sourceSystem;
        this.employeeNumber = employeeNumber;
        this.displayName = displayName;
        this.externalDepartmentId = externalDepartmentId;
        this.jobTitle = jobTitle;
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
}
