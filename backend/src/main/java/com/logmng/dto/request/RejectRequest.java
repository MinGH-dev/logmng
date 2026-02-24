package com.logmng.dto.request;

/**
 * 검색 이력 반려 요청 본문 (선택: rejectionReason)
 */
public class RejectRequest {

    private String rejectionReason;

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
