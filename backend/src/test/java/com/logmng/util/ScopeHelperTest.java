package com.logmng.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ScopeHelper (team scope, default 'team', self/all).
 * Req: 20250304-team-scope-default-and-approval.
 */
class ScopeHelperTest {

    private static final String ACTIVITY_LOG = "activity-log";
    private static final String STATISTICS = "statistics";
    private static final String SEARCH_HISTORY = "search-history";
    private static final String MAIN = "main";

    // --- screen does not support scope → "all" ---
    @Test
    void resolveScope_nonScopeScreen_returnsAll() {
        assertThat(ScopeHelper.resolveScope(MAIN, false, null)).isEqualTo("all");
        assertThat(ScopeHelper.resolveScope("user-management", false, Map.of(ACTIVITY_LOG, "self"))).isEqualTo("all");
    }

    // --- isSystemAdmin → "all" ---
    @Test
    void resolveScope_systemAdmin_returnsAll() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, true, null)).isEqualTo("all");
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, true, Map.of(ACTIVITY_LOG, "team"))).isEqualTo("all");
    }

    // --- default when null/omitted (scope-supporting screen) = "team" ---
    @Test
    void resolveScope_scopeSupportingScreen_nullScreenScopes_returnsTeam() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, null)).isEqualTo("team");
        assertThat(ScopeHelper.resolveScope(STATISTICS, false, null)).isEqualTo("team");
        assertThat(ScopeHelper.resolveScope(SEARCH_HISTORY, false, null)).isEqualTo("team");
    }

    @Test
    void resolveScope_scopeSupportingScreen_emptyScreenScopes_returnsTeam() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Collections.emptyMap())).isEqualTo("team");
    }

    @Test
    void resolveScope_scopeSupportingScreen_screenNotInMap_returnsTeam() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of("other-screen", "all"))).isEqualTo("team");
    }

    // --- explicit team / self / all ---
    @Test
    void resolveScope_explicitTeam_returnsTeam() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "team"))).isEqualTo("team");
    }

    @Test
    void resolveScope_explicitSelf_returnsSelf() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "self"))).isEqualTo("self");
    }

    @Test
    void resolveScope_explicitAll_returnsAll() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "all"))).isEqualTo("all");
    }

    // --- case insensitivity ---
    @Test
    void resolveScope_caseInsensitive() {
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "TEAM"))).isEqualTo("team");
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "Self"))).isEqualTo("self");
        assertThat(ScopeHelper.resolveScope(ACTIVITY_LOG, false, Map.of(ACTIVITY_LOG, "ALL"))).isEqualTo("all");
    }
}
