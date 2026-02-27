package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Permission group API response (§14). id, code, name, description, sortOrder
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionGroupResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    @JsonProperty("sortOrder")
    private Integer sortOrder;
    private List<String> allowedScreens;

    public PermissionGroupResponse() {
    }

    public PermissionGroupResponse(Long id, String code, String name, String description, Integer sortOrder) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public List<String> getAllowedScreens() {
        return allowedScreens;
    }

    public void setAllowedScreens(List<String> allowedScreens) {
        this.allowedScreens = allowedScreens;
    }
}
