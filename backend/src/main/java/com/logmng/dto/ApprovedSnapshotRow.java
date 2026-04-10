package com.logmng.dto;

/**
 * One row in search_history_approved_row: logical row_id + row_status (empty for pb_feplog).
 */
public final class ApprovedSnapshotRow {

    private final String rowId;
    private final String rowStatus;

    public ApprovedSnapshotRow(String rowId, String rowStatus) {
        this.rowId = rowId != null ? rowId.trim() : "";
        this.rowStatus = DecryptionRowKey.normalizeStatus(rowStatus);
    }

    public String getRowId() {
        return rowId;
    }

    public String getRowStatus() {
        return rowStatus;
    }

    public boolean isEmpty() {
        return rowId.isEmpty();
    }

    /** For java_fw_imglog decryption-allowed refresh only. */
    public DecryptionRowKey toDecryptionRowKey() {
        return new DecryptionRowKey(rowId, rowStatus);
    }
}
