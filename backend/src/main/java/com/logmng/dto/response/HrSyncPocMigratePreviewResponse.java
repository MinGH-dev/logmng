package com.logmng.dto.response;

/** {@code data} for {@code POST /api/hr-sync/poc/user-mgmt/actions/migrate-preview} (no-op stub). */
public class HrSyncPocMigratePreviewResponse {

    private boolean persisted;
    private String messageCode;

    public HrSyncPocMigratePreviewResponse() {
    }

    public HrSyncPocMigratePreviewResponse(boolean persisted, String messageCode) {
        this.persisted = persisted;
        this.messageCode = messageCode;
    }

    public boolean isPersisted() {
        return persisted;
    }

    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }
}
