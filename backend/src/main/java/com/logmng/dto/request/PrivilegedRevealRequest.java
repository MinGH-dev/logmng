package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/activity-log/{id}/privileged-reveal
 */
public class PrivilegedRevealRequest {

    @JsonProperty("revealKind")
    private String revealKind;

    public String getRevealKind() {
        return revealKind;
    }

    public void setRevealKind(String revealKind) {
        this.revealKind = revealKind;
    }
}
