package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal permission group for hierarchy user node (§14.9). id, code, name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionGroupSummary {

    private Long id;
    private String code;
    private String name;

    public PermissionGroupSummary() {
    }

    public PermissionGroupSummary(Long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
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
}
