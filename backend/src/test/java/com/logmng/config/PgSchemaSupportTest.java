package com.logmng.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgSchemaSupportTest {

    @Test
    void requireValidSchemaName_defaultsToPublic() {
        assertThat(PgSchemaSupport.requireValidSchemaName(null)).isEqualTo("public");
        assertThat(PgSchemaSupport.requireValidSchemaName("")).isEqualTo("public");
        assertThat(PgSchemaSupport.requireValidSchemaName("  ")).isEqualTo("public");
    }

    @Test
    void requireValidSchemaName_rejectsInvalid() {
        assertThatThrownBy(() -> PgSchemaSupport.requireValidSchemaName("'; DROP--"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildPrimarySearchPathInitSql_ordersSysPbPublic() {
        assertThat(PgSchemaSupport.buildPrimarySearchPathInitSql("logmng_sys", "logmng"))
                .isEqualTo("SET search_path TO logmng_sys, logmng, public");
    }

    @Test
    void buildPrimarySearchPathInitSql_dedupesWhenSame() {
        assertThat(PgSchemaSupport.buildPrimarySearchPathInitSql("public", "public"))
                .isEqualTo("SET search_path TO public");
    }
}
