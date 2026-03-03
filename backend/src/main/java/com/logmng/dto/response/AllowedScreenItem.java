package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Screen item with optional scope for permission group allowedScreens.
 * Per specs/permission-group-hierarchy.spec.yaml §1.1: allowedScreens: [{ screenId, scope? }].
 * Scope applies only to activity-log, statistics, search-history; null/omitted = 'self'.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllowedScreenItem {

    @JsonProperty("screenId")
    private String screenId;

    /** 'self' | 'all'. Only for activity-log, statistics, search-history. Null = 'self'. */
    private String scope;

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
}
