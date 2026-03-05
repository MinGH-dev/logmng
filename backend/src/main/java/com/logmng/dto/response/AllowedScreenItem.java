package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Screen item with optional scope and read/write/approve for permission group allowedScreens.
 * Per specs/permission-group-hierarchy.spec.yaml §1.1: allowedScreens: [{ screenId, scope?, read?, write?, approve? }].
 * Scope applies only to activity-log, statistics, search-history; null/omitted = 'team' (default).
 * read/write/approve: explicit per-screen functions; null = use derived default (backward compat).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllowedScreenItem {

    @JsonProperty("screenId")
    private String screenId;

    /** 'self' | 'team' | 'all'. Only for activity-log, statistics, search-history. Null = 'team'. */
    private String scope;

    /** Explicit read flag. Null = use derived (true when screen present). */
    private Boolean read;

    /** Explicit write flag. Null = use derived. Only for screens that support write. */
    private Boolean write;

    /** Explicit approve flag. Null = use derived. Only for search-history, pending-approvals. */
    private Boolean approve;

    public AllowedScreenItem() {
    }

    public AllowedScreenItem(String screenId, String scope) {
        this.screenId = screenId;
        this.scope = scope;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Boolean getWrite() {
        return write;
    }

    public void setWrite(Boolean write) {
        this.write = write;
    }

    public Boolean getApprove() {
        return approve;
    }

    public void setApprove(Boolean approve) {
        this.approve = approve;
    }
}
