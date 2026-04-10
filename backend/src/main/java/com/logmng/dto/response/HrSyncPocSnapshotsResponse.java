package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code data} for {@code GET /api/hr-sync/poc/snapshots}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocSnapshotsResponse {

    private List<HrSyncPocSnapshotItem> snapshots;

    public HrSyncPocSnapshotsResponse() {
    }

    public HrSyncPocSnapshotsResponse(List<HrSyncPocSnapshotItem> snapshots) {
        this.snapshots = snapshots;
    }

    public List<HrSyncPocSnapshotItem> getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(List<HrSyncPocSnapshotItem> snapshots) {
        this.snapshots = snapshots;
    }
}
