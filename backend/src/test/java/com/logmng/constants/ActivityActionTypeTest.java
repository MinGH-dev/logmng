package com.logmng.constants;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityActionTypeTest {

    @Test
    void filterDropdownOptions_sortedByCode_excludesUnknown() {
        List<Map<String, String>> opts = ActivityActionType.filterDropdownOptions();
        assertThat(opts.get(0).get("code")).isEqualTo("ADVANCED_SEARCH");
        List<String> codes = opts.stream().map(m -> m.get("code")).collect(Collectors.toList());
        assertThat(codes).doesNotContain("UNKNOWN");
        assertThat(opts).allMatch(m -> m.containsKey("code") && m.containsKey("label"));
    }

    @Test
    void maxLength_matchesSchemaVarchar50() {
        assertThat(ActivityActionType.MAX_ACTION_TYPE_LENGTH).isEqualTo(50);
    }
}
