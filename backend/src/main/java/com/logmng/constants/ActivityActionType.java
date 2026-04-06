package com.logmng.constants;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canonical {@code user_activity_log.action_type} codes per {@code specs/activity-action-types.spec.yaml}.
 * Single source for {@code @ActivityLog(actionType=…)}, validation max length, and GET /api/activity-log/action-types.
 */
public enum ActivityActionType {

    // §2.1 existing
    LOGIN("LOGIN", "로그인", true),
    LOGOUT("LOGOUT", "로그아웃", true),
    SEARCH("SEARCH", "검색", true),
    VIEW("VIEW", "상세 조회", true),
    DECRYPT("DECRYPT", "복호화", true),
    ADVANCED_SEARCH("ADVANCED_SEARCH", "고급 검색", true),

    // §2.2 inferred / generic
    EXPORT("EXPORT", "내보내기", true),
    STATS_VIEW("STATS_VIEW", "통계 조회", true),
    SCHEMA_VIEW("SCHEMA_VIEW", "스키마 조회", true),
    UNKNOWN("UNKNOWN", "기타", false),

    // §2.3 permission group
    PERMISSION_GROUP_CREATE("PERMISSION_GROUP_CREATE", "권한 그룹 생성", true),
    PERMISSION_GROUP_UPDATE("PERMISSION_GROUP_UPDATE", "권한 그룹 수정", true),
    PERMISSION_GROUP_DELETE("PERMISSION_GROUP_DELETE", "권한 그룹 삭제", true),
    ASSIGN_USER_TO_PERMISSION_GROUP("ASSIGN_USER_TO_PERMISSION_GROUP", "권한 그룹 사용자 배정", true),
    UNASSIGN_USER_FROM_PERMISSION_GROUP("UNASSIGN_USER_FROM_PERMISSION_GROUP", "권한 그룹 사용자 해제", true),

    // §2.4 user / org admin (reserved for future emission)
    USER_CREATE("USER_CREATE", "사용자 생성", true),
    USER_UPDATE("USER_UPDATE", "사용자 수정", true),
    USER_DELETE("USER_DELETE", "사용자 삭제", true),
    DEPARTMENT_APPROVER_UPDATE("DEPARTMENT_APPROVER_UPDATE", "부서 결재자 변경", true),

    // §2.5 reserved
    SEARCH_HISTORY_CREATE("SEARCH_HISTORY_CREATE", "검색 이력 생성", true),
    DECRYPT_APPROVAL_APPROVE("DECRYPT_APPROVAL_APPROVE", "복호화 승인", true),
    DECRYPT_APPROVAL_REJECT("DECRYPT_APPROVAL_REJECT", "복호화 반려", true),

    // §2.6 optional list view
    USER_LIST_VIEW("USER_LIST_VIEW", "사용자 목록 조회", true),

    /** Screen/menu display label admin save. Req: 20260406-menu-display-names-admin. */
    SCREEN_DISPLAY_LABELS_UPDATE("SCREEN_DISPLAY_LABELS_UPDATE", "화면 표시 라벨 변경", true),

    /** In-app copy; {@code action_detail.copyPayload} per specs/activity-log-audit-evidence.spec.yaml §4.1. */
    IN_APP_COPY("IN_APP_COPY", "인앱 복사", true);

    public static final int MAX_ACTION_TYPE_LENGTH = 50;

    private final String code;
    private final String labelKo;
    private final boolean includeInFilterDropdown;

    ActivityActionType(String code, String labelKo, boolean includeInFilterDropdown) {
        this.code = code;
        this.labelKo = labelKo;
        this.includeInFilterDropdown = includeInFilterDropdown;
    }

    public String getCode() {
        return code;
    }

    /** Korean label for UI (contract field {@code label}). */
    public String getLabelKo() {
        return labelKo;
    }

    public boolean isIncludeInFilterDropdown() {
        return includeInFilterDropdown;
    }

    /**
     * Options for GET /api/activity-log/action-types: {@code code} ascending, excludes UNKNOWN.
     */
    public static List<Map<String, String>> filterDropdownOptions() {
        return Arrays.stream(values())
                .filter(ActivityActionType::isIncludeInFilterDropdown)
                .sorted(Comparator.comparing(ActivityActionType::getCode))
                .map(e -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("code", e.getCode());
                    row.put("label", e.getLabelKo());
                    return row;
                })
                .collect(Collectors.toList());
    }
}
