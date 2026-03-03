package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.logmng.dto.response.AllowedScreenItem;

import java.util.List;

/**
 * Permission group update request (§14.4). All fields optional.
 * allowedScreens: [{ screenId, scope? }] or string[] (backward compat) per specs/permission-group-hierarchy.spec.yaml.
 */
public class PermissionGroupUpdateRequest {

    private String code;
    private String name;
    private String description;
    @JsonProperty("sortOrder")
    private Integer sortOrder;

    @JsonDeserialize(using = AllowedScreenListDeserializer.class)
    private List<AllowedScreenItem> allowedScreens;

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

    public List<AllowedScreenItem> getAllowedScreens() {
        return allowedScreens;
    }

    public void setAllowedScreens(List<AllowedScreenItem> allowedScreens) {
        this.allowedScreens = allowedScreens;
    }
}
