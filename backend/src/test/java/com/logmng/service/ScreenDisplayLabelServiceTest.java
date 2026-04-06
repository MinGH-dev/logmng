package com.logmng.service;

import com.logmng.dto.request.ScreenDisplayLabelItemRequest;
import com.logmng.dto.request.ScreenDisplayLabelsPutRequest;
import com.logmng.exception.CustomException;
import com.logmng.repository.ScreenDisplayLabelDataAccess;
import com.logmng.repository.ScreenDisplayLabelRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TC-04, TC-05 and validation for screen display labels.
 */
@ExtendWith(MockitoExtension.class)
class ScreenDisplayLabelServiceTest {

    @Mock
    private ScreenDisplayLabelDataAccess screenDisplayLabelDataAccess;

    @InjectMocks
    private ScreenDisplayLabelService screenDisplayLabelService;

    @Test
    void replaceAll_unknownScreenId_throwsInvalidScreenIdAndDoesNotUpsert() throws Exception {
        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("not-a-real-screen-id");
        item.setLabelUser("ok");
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INVALID_SCREEN_ID"));

        verify(screenDisplayLabelDataAccess, never()).upsertAll(anyList());
    }

    @Test
    void replaceAll_labelUserTooLong_throwsInvalidInput() throws Exception {
        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("pb-feplog");
        item.setLabelUser("x".repeat(ScreenDisplayLabelService.MAX_LABEL_LENGTH + 1));
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));

        verify(screenDisplayLabelDataAccess, never()).upsertAll(anyList());
    }

    @Test
    void replaceAll_blankLabelUser_throwsInvalidInput() {
        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("pb-feplog");
        item.setLabelUser("   ");
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));
    }

    @Test
    void replaceAll_valid_callsUpsertAndReturnsList() throws Exception {
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of());

        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("pb-feplog");
        item.setLabelUser("PB FEP");
        item.setLabelAdmin("note");
        body.setLabels(List.of(item));

        screenDisplayLabelService.replaceAll(body, 99L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScreenDisplayLabelRow>> cap = ArgumentCaptor.forClass(List.class);
        verify(screenDisplayLabelDataAccess).upsertAll(cap.capture());
        assertThat(cap.getValue()).hasSize(1);
        assertThat(cap.getValue().get(0).getScreenId()).isEqualTo("pb-feplog");
        assertThat(cap.getValue().get(0).getLabelUser()).isEqualTo("PB FEP");
        assertThat(cap.getValue().get(0).getLabelAdmin()).isEqualTo("note");
        assertThat(cap.getValue().get(0).getParentGroupId()).isNull();
        assertThat(cap.getValue().get(0).getSortOrder()).isEqualTo(0);
        assertThat(cap.getValue().get(0).getUpdatedBy()).isEqualTo(99L);
    }

    @Test
    void replaceAll_invalidParentGroupId_throwsInvalidInput() throws Exception {
        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("activity-log");
        item.setLabelUser("활동 이력");
        item.setParentGroupId("unknown-group");
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));

        verify(screenDisplayLabelDataAccess, never()).upsertAll(anyList());
    }

    @Test
    void replaceAll_negativeSortOrder_throwsInvalidInput() throws Exception {
        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("activity-log");
        item.setLabelUser("활동 이력");
        item.setSortOrder(-1);
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));

        verify(screenDisplayLabelDataAccess, never()).upsertAll(anyList());
    }

    @Test
    void replaceAll_parentAndSort_persisted() throws Exception {
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of());

        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("activity-log");
        item.setLabelUser("활동 이력");
        item.setParentGroupId("history");
        item.setSortOrder(2);
        body.setLabels(List.of(item));

        screenDisplayLabelService.replaceAll(body, 99L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScreenDisplayLabelRow>> cap = ArgumentCaptor.forClass(List.class);
        verify(screenDisplayLabelDataAccess).upsertAll(cap.capture());
        assertThat(cap.getValue().get(0).getParentGroupId()).isEqualTo("history");
        assertThat(cap.getValue().get(0).getSortOrder()).isEqualTo(2);
    }

    @Test
    void listForViewer_nullSortOrder_mapsToZero() throws Exception {
        ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
        row.setScreenId("activity-log");
        row.setLabelUser("U");
        row.setSortOrder(null);
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of(row));

        var list = screenDisplayLabelService.listForViewer(false);
        assertThat(list.get(0).getSortOrder()).isEqualTo(0);
    }

    @Test
    void listForViewer_nonAdmin_includesParentAndSort() throws Exception {
        ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
        row.setScreenId("activity-log");
        row.setLabelUser("U");
        row.setLabelAdmin("secret");
        row.setParentGroupId("history");
        row.setSortOrder(2);
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of(row));

        var list = screenDisplayLabelService.listForViewer(false);
        assertThat(list.get(0).getParentGroupId()).isEqualTo("history");
        assertThat(list.get(0).getSortOrder()).isEqualTo(2);
        assertThat(list.get(0).getLabelAdmin()).isNull();
    }

    @Test
    void listForViewer_nonAdminOmitsAdminColumn() throws Exception {
        ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
        row.setScreenId("pb-feplog");
        row.setLabelUser("U");
        row.setLabelAdmin("secret");
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of(row));

        var list = screenDisplayLabelService.listForViewer(false);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getLabelAdmin()).isNull();
        assertThat(list.get(0).getLabelUser()).isEqualTo("U");
    }

    @Test
    void listForViewer_adminIncludesAdminColumn() throws Exception {
        ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
        row.setScreenId("pb-feplog");
        row.setLabelUser("U");
        row.setLabelAdmin("secret");
        when(screenDisplayLabelDataAccess.findAllOrdered()).thenReturn(List.of(row));

        var list = screenDisplayLabelService.listForViewer(true);
        assertThat(list.get(0).getLabelAdmin()).isEqualTo("secret");
    }

    @Test
    void replaceAll_sqlFailure_wrapsInternalError() throws Exception {
        org.mockito.Mockito.doThrow(new SQLException("boom")).when(screenDisplayLabelDataAccess).upsertAll(anyList());

        ScreenDisplayLabelsPutRequest body = new ScreenDisplayLabelsPutRequest();
        ScreenDisplayLabelItemRequest item = new ScreenDisplayLabelItemRequest();
        item.setScreenId("pb-feplog");
        item.setLabelUser("x");
        body.setLabels(List.of(item));

        assertThatThrownBy(() -> screenDisplayLabelService.replaceAll(body, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR"));
    }
}
