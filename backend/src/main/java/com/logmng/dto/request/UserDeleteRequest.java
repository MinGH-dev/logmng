package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DELETE /api/users/{userId} body (req 20260407-user-management-consistency-delete-reason-activity-audit).
 */
public class UserDeleteRequest {

    @JsonProperty("changeReason")
    private String changeReason;

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
