package com.logmng.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 결재자 추가 요청 본문 (§7.2)
 */
public class AddApproverRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
