package com.logmng.dto;

import java.util.Objects;

/**
 * Canonical composite identity for java_fw_imglog rows: (guid, status).
 * Req: docs/requirements/20260320-imagelog-guid-status-composite-key.md
 */
public final class DecryptionRowKey {

    private final String guid;
    /** Business status; empty string means legacy / pb snapshot rows not using composite (DB row_status ''). */
    private final String status;

    public DecryptionRowKey(String guid, String status) {
        this.guid = guid != null ? guid.trim() : "";
        this.status = normalizeStatus(status);
    }

    public static String normalizeStatus(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }

    public String getGuid() {
        return guid;
    }

    public String getStatus() {
        return status;
    }

    public boolean isValidForImagelogDecrypt() {
        return !guid.isEmpty() && !status.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DecryptionRowKey that = (DecryptionRowKey) o;
        return guid.equals(that.guid) && status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, status);
    }

    /** Stable key for maps (guid must not contain unit separator). */
    public String compositeMapKey() {
        return guid + "\u001f" + status;
    }
}
