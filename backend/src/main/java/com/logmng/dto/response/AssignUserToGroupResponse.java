package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for POST /api/permission-groups/{id}/users (§14.6).
 * userId = numeric app_user.id (req 20260316), permissionGroupId, permissionGroupCode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignUserToGroupResponse {

    private Long userId;
    @JsonProperty("permissionGroupId")
    private Long permissionGroupId;
    @JsonProperty("permissionGroupCode")
    private String permissionGroupCode;

    public AssignUserToGroupResponse() {
    }

    public AssignUserToGroupResponse(Long userId, Long permissionGroupId, String permissionGroupCode) {
        this.userId = userId;
        this.permissionGroupId = permissionGroupId;
        this.permissionGroupCode = permissionGroupCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPermissionGroupId() {
        return permissionGroupId;
    }

    public void setPermissionGroupId(Long permissionGroupId) {
        this.permissionGroupId = permissionGroupId;
    }

    public String getPermissionGroupCode() {
        return permissionGroupCode;
    }

    public void setPermissionGroupCode(String permissionGroupCode) {
        this.permissionGroupCode = permissionGroupCode;
    }
}
