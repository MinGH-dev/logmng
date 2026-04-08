package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/hr-sync/poc/preview success {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HrSyncPocPreviewResponse {

    private String previewId;
    private String snapshotId;
    private HrSyncPocClassificationCounts classificationCounts;
    private String riskTier;
    private String upstreamGateStatus;
    private String messageCode;

    public HrSyncPocPreviewResponse() {
    }

    public HrSyncPocPreviewResponse(
            String previewId,
            String snapshotId,
            HrSyncPocClassificationCounts classificationCounts,
            String riskTier,
            String upstreamGateStatus,
            String messageCode) {
        this.previewId = previewId;
        this.snapshotId = snapshotId;
        this.classificationCounts = classificationCounts;
        this.riskTier = riskTier;
        this.upstreamGateStatus = upstreamGateStatus;
        this.messageCode = messageCode;
    }

    public String getPreviewId() {
        return previewId;
    }

    public void setPreviewId(String previewId) {
        this.previewId = previewId;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public HrSyncPocClassificationCounts getClassificationCounts() {
        return classificationCounts;
    }

    public void setClassificationCounts(HrSyncPocClassificationCounts classificationCounts) {
        this.classificationCounts = classificationCounts;
    }

    public String getRiskTier() {
        return riskTier;
    }

    public void setRiskTier(String riskTier) {
        this.riskTier = riskTier;
    }

    public String getUpstreamGateStatus() {
        return upstreamGateStatus;
    }

    public void setUpstreamGateStatus(String upstreamGateStatus) {
        this.upstreamGateStatus = upstreamGateStatus;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }
}
