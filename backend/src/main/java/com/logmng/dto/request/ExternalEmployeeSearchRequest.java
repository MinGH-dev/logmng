package com.logmng.dto.request;

/**
 * POST /api/provisioning/external-employees/search
 */
public class ExternalEmployeeSearchRequest {

    private String keyword;
    private String employeeNumber;
    private String externalDepartmentId;
    private String sourceSystem;
    private Integer page = 1;
    private Integer pageSize = 20;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getExternalDepartmentId() {
        return externalDepartmentId;
    }

    public void setExternalDepartmentId(String externalDepartmentId) {
        this.externalDepartmentId = externalDepartmentId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
