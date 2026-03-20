package com.logmng.service;

/**
 * 복호화 승인 검사 실패 시 진단 정보 (로그용, ID만 포함·PII 미포함).
 * req 20260317-image-log-decrypt-error-root-cause-and-data-validation
 */
public final class ApprovalFailureDiagnostic {

    private final ApprovalFailureReason reason;
    private final Long rowUserId;
    private final String approvalStatus;
    private final Boolean expired;

    public ApprovalFailureDiagnostic(ApprovalFailureReason reason, Long rowUserId, String approvalStatus, Boolean expired) {
        this.reason = reason;
        this.rowUserId = rowUserId;
        this.approvalStatus = approvalStatus;
        this.expired = expired;
    }

    public ApprovalFailureReason getReason() {
        return reason;
    }

    public Long getRowUserId() {
        return rowUserId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public Boolean getExpired() {
        return expired;
    }
}
