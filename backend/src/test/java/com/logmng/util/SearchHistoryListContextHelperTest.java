package com.logmng.util;

import com.logmng.constants.ScreenConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchHistoryListContextHelperTest {

    @Test
    void resolve_omitted_prefersSearchHistoryWhenBoth() throws Exception {
        String s = SearchHistoryListContextHelper.resolveEffectiveScreenId(null,
                List.of(ScreenConstants.SEARCH_HISTORY, ScreenConstants.PENDING_APPROVALS));
        assertThat(s).isEqualTo(ScreenConstants.SEARCH_HISTORY);
    }

    @Test
    void resolve_omitted_onlyPendingApprovals() throws Exception {
        String s = SearchHistoryListContextHelper.resolveEffectiveScreenId("",
                List.of(ScreenConstants.PENDING_APPROVALS));
        assertThat(s).isEqualTo(ScreenConstants.PENDING_APPROVALS);
    }

    @Test
    void resolve_explicit_pendingApprovals() throws Exception {
        String s = SearchHistoryListContextHelper.resolveEffectiveScreenId("pending-approvals",
                List.of(ScreenConstants.PENDING_APPROVALS));
        assertThat(s).isEqualTo(ScreenConstants.PENDING_APPROVALS);
    }

    @Test
    void resolve_explicit_searchHistory_whenNotAllowed_throws() {
        assertThatThrownBy(() -> SearchHistoryListContextHelper.resolveEffectiveScreenId("search-history",
                List.of(ScreenConstants.PENDING_APPROVALS)))
                .isInstanceOf(SearchHistoryListContextHelper.ListContextResolutionException.class);
    }

    @Test
    void resolve_invalidValue_throws() {
        assertThatThrownBy(() -> SearchHistoryListContextHelper.resolveEffectiveScreenId("other",
                List.of(ScreenConstants.SEARCH_HISTORY)))
                .isInstanceOf(SearchHistoryListContextHelper.ListContextResolutionException.class);
    }
}
