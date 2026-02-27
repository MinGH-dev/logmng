package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Permission group update request (§14.4). All fields optional.
 */
public class PermissionGroupUpdateRequest {

    private String code;
    private String name;
    private String description;
    @JsonProperty("sortOrder")
    private Integer sortOrder;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
