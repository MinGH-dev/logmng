package com.logmng.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HR Sync PoC flags (preview-only). YAML {@code hr.sync.poc.*}; env e.g. {@code HR_SYNC_POC_ENABLED}.
 * See {@code specs/hr-sync-poc.spec.yaml}, {@code docs/contract.md} § HR Sync PoC.
 */
@ConfigurationProperties(prefix = "hr.sync.poc")
public class HrSyncPocProperties {

    /**
     * When false, PoC routes return 403 {@code POC_DISABLED} after auth/screen checks.
     */
    private boolean enabled = false;

    /**
     * Apply path gate (future); does not enable mutation from preview.
     */
    private boolean applyEnabled = false;

    /**
     * Product default execution mode label; e.g. PREVIEW_ONLY.
     */
    private String defaultMode = "PREVIEW_ONLY";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isApplyEnabled() {
        return applyEnabled;
    }

    public void setApplyEnabled(boolean applyEnabled) {
        this.applyEnabled = applyEnabled;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode != null ? defaultMode.trim() : "PREVIEW_ONLY";
    }
}
