package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GET /api/hr-sync/poc/config response {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocConfigResponse {

    private boolean pocEnabled;
    private String defaultMode;
    private boolean applyEnabled;

    public HrSyncPocConfigResponse() {
    }

    public HrSyncPocConfigResponse(boolean pocEnabled, String defaultMode, boolean applyEnabled) {
        this.pocEnabled = pocEnabled;
        this.defaultMode = defaultMode;
        this.applyEnabled = applyEnabled;
    }

    public boolean isPocEnabled() {
        return pocEnabled;
    }

    public void setPocEnabled(boolean pocEnabled) {
        this.pocEnabled = pocEnabled;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public boolean isApplyEnabled() {
        return applyEnabled;
    }

    public void setApplyEnabled(boolean applyEnabled) {
        this.applyEnabled = applyEnabled;
    }
}
