package com.logmng.dto.request;

/**
 * POST /api/provisioning/users/from-external-employee
 */
public class ProvisionFromExternalEmployeeRequest {

    private String externalEmployeeId;
    private String sourceSystem;
    private String departmentCode;
    /** Required for audit; same rules as permission-group changeReason (max 500, trim, non-empty). */
    private String changeReason;

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

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
