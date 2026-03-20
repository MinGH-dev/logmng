package com.logmng.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TC-05: migration check uses configurable system schema (not hardcoded public). */
class SearchHistoryUserIdMigrationCheckSqlTest {

    @Test
    void searchHistoryUserIdColumnTypeSql_usesLogmngSys() {
        String sql = SearchHistoryUserIdMigrationCheck.searchHistoryUserIdColumnTypeSql("logmng_sys");
        assertThat(sql).contains("table_schema = 'logmng_sys'");
        assertThat(sql).doesNotContain("table_schema = 'public'");
    }

    @Test
    void searchHistoryUserIdColumnTypeSql_defaultPublic() {
        String sql = SearchHistoryUserIdMigrationCheck.searchHistoryUserIdColumnTypeSql("public");
        assertThat(sql).contains("table_schema = 'public'");
    }
}
