package com.logmng.service;

/**
 * 복호화 승인 검사 실패 사유 (진단용, PII 미포함).
 * req 20260317-image-log-decrypt-error-root-cause-and-data-validation
 */
public enum ApprovalFailureReason {
    /** 검색 이력 행 없음 */
    ROW_NOT_FOUND,
    /** 행은 존재·APPROVED·미만료이나 user_id != 현재 사용자 (실행자가 요청자가 아님) */
    USER_MISMATCH,
    /** approval_status != APPROVED */
    NOT_APPROVED,
    /** expires_at <= 현재 시각 */
    EXPIRED
}
