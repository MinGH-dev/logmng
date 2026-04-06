package com.logmng.service;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.ScreenDisplayLabelItemRequest;
import com.logmng.dto.request.ScreenDisplayLabelsPutRequest;
import com.logmng.dto.response.ScreenDisplayLabelItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.repository.ScreenDisplayLabelDataAccess;
import com.logmng.repository.ScreenDisplayLabelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-configurable screen/menu display labels. Req: 20260406-menu-display-names-admin, 20260407-screen-menu-parent-order.
 */
@Service
public class ScreenDisplayLabelService implements ScreenDisplayLabelApi {

    private static final Logger log = LoggerFactory.getLogger(ScreenDisplayLabelService.class);
    static final int MAX_LABEL_LENGTH = 256;

    private final ScreenDisplayLabelDataAccess screenDisplayLabelDataAccess;

    public ScreenDisplayLabelService(ScreenDisplayLabelDataAccess screenDisplayLabelDataAccess) {
        this.screenDisplayLabelDataAccess = screenDisplayLabelDataAccess;
    }

    @Override
    public List<ScreenDisplayLabelItemResponse> listForViewer(boolean systemAdmin) {
        try {
            List<ScreenDisplayLabelRow> rows = screenDisplayLabelDataAccess.findAllOrdered();
            List<ScreenDisplayLabelItemResponse> out = new ArrayList<>(rows.size());
            for (ScreenDisplayLabelRow row : rows) {
                String admin = systemAdmin ? row.getLabelAdmin() : null;
                Integer sortOrder = row.getSortOrder() != null ? row.getSortOrder() : 0;
                out.add(new ScreenDisplayLabelItemResponse(
                        row.getScreenId(),
                        row.getLabelUser(),
                        admin,
                        row.getParentGroupId(),
                        sortOrder));
            }
            return out;
        } catch (SQLException e) {
            log.error("screen_display_label list failed", e);
            throw CustomException.internalError("화면 표시 라벨을 조회할 수 없습니다.", "INTERNAL_SERVER_ERROR");
        }
    }

    @Override
    public List<ScreenDisplayLabelItemResponse> replaceAll(ScreenDisplayLabelsPutRequest body, long actorAppUserId) {
        List<ScreenDisplayLabelItemRequest> labels = body != null ? body.getLabels() : null;
        if (labels == null) {
            labels = List.of();
        }
        List<ScreenDisplayLabelRow> rows = new ArrayList<>(labels.size());
        for (ScreenDisplayLabelItemRequest item : labels) {
            if (item == null) {
                throw CustomException.badRequest("labels 항목이 올바르지 않습니다.", "INVALID_INPUT");
            }
            String normalizedId = ScreenConstants.normalizeScreenIdForPermissionGroup(item.getScreenId());
            if (normalizedId == null || normalizedId.isBlank()) {
                throw CustomException.badRequest("screenId는 필수입니다.", "INVALID_INPUT");
            }
            if (!ScreenConstants.isValid(normalizedId)) {
                throw CustomException.badRequest("유효하지 않은 화면 ID입니다: " + item.getScreenId(), "INVALID_SCREEN_ID");
            }
            String labelUser = item.getLabelUser();
            if (labelUser == null || labelUser.trim().isEmpty()) {
                throw CustomException.badRequest("labelUser는 비어 있을 수 없습니다.", "INVALID_INPUT");
            }
            String trimmedUser = labelUser.trim();
            validatePlainText(trimmedUser, "labelUser");

            String labelAdmin = null;
            if (item.getLabelAdmin() != null) {
                String ta = item.getLabelAdmin().trim();
                if (!ta.isEmpty()) {
                    validatePlainText(ta, "labelAdmin");
                    labelAdmin = ta;
                }
            }

            String parentGroupId = normalizeOptionalParentGroupId(item.getParentGroupId());
            Integer sortOrder = validateOptionalSortOrder(item.getSortOrder());
            if (sortOrder == null) {
                sortOrder = 0;
            }

            ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
            row.setScreenId(normalizedId);
            row.setLabelUser(trimmedUser);
            row.setLabelAdmin(labelAdmin);
            row.setParentGroupId(parentGroupId);
            row.setSortOrder(sortOrder);
            row.setUpdatedBy(actorAppUserId);
            rows.add(row);
        }
        try {
            screenDisplayLabelDataAccess.upsertAll(rows);
        } catch (SQLException e) {
            log.error("screen_display_label upsert failed: sqlState={} message={}",
                    e.getSQLState(), e.getMessage(), e);
            throw CustomException.internalError("화면 표시 라벨을 저장할 수 없습니다.", "INTERNAL_SERVER_ERROR");
        }
        return listForViewer(true);
    }

    /**
     * Null or blank after trim → persist NULL (client MENU_TREE default). Non-blank must be in {@link ScreenConstants#PARENT_GROUP_IDS}.
     */
    static String normalizeOptionalParentGroupId(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!ScreenConstants.PARENT_GROUP_IDS.contains(t)) {
            throw CustomException.badRequest("유효하지 않은 parentGroupId입니다.", "INVALID_INPUT");
        }
        return t;
    }

    /** Null → null (caller coerces to 0 before persist). Non-null must be {@code >= 0}. */
    static Integer validateOptionalSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return null;
        }
        if (sortOrder < 0) {
            throw CustomException.badRequest("sortOrder는 0 이상의 정수여야 합니다.", "INVALID_INPUT");
        }
        return sortOrder;
    }

    private static void validatePlainText(String value, String fieldName) {
        if (value.length() > MAX_LABEL_LENGTH) {
            throw CustomException.badRequest(fieldName + "은(는) " + MAX_LABEL_LENGTH + "자 이하여야 합니다.", "INVALID_INPUT");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127) {
                throw CustomException.badRequest(fieldName + "에 허용되지 않는 문자가 포함되어 있습니다.", "INVALID_INPUT");
            }
        }
    }
}
