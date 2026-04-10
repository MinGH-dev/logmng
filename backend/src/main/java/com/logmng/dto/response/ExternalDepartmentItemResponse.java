package com.logmng.dto.response;

public class ExternalDepartmentItemResponse {

    private String externalDepartmentId;
    private String sourceSystem;
    private String name;
    private String parentExternalDepartmentId;

    public ExternalDepartmentItemResponse() {
    }

    public ExternalDepartmentItemResponse(String externalDepartmentId, String sourceSystem, String name,
                                          String parentExternalDepartmentId) {
        this.externalDepartmentId = externalDepartmentId;
        this.sourceSystem = sourceSystem;
        this.name = name;
        this.parentExternalDepartmentId = parentExternalDepartmentId;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentExternalDepartmentId() {
        return parentExternalDepartmentId;
    }

    public void setParentExternalDepartmentId(String parentExternalDepartmentId) {
        this.parentExternalDepartmentId = parentExternalDepartmentId;
    }
}
