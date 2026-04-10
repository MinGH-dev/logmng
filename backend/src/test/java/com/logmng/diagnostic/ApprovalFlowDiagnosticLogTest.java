package com.logmng.diagnostic;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApprovalFlowDiagnosticLogTest {

    @Test
    void info_whenDisabled_doesNotThrow() {
        assertThatCode(() -> ApprovalFlowDiagnosticLog.info(false, 1L, 2L, "PHASE", "x")).doesNotThrowAnyException();
    }

    @Test
    void logSqlException_whenDisabled_doesNotThrow() {
        SQLException ex = new SQLException("test", "23505", 0);
        assertThatCode(() -> ApprovalFlowDiagnosticLog.logSqlException(false, 1L, "PHASE", ex)).doesNotThrowAnyException();
    }

    @Test
    void logSqlException_withNullSearchHistoryId_whenDisabled_doesNotThrow() {
        SQLException ex = new SQLException("dup", "23505", 0);
        assertThatCode(() -> ApprovalFlowDiagnosticLog.logSqlException(false, null, 40L, "main", "PHASE", ex)).doesNotThrowAnyException();
    }

    @Test
    void parseConstraintName_findsUniqueConstraint() {
        String msg = "ERROR: duplicate key value violates unique constraint \"user_decryption_allowed_pkey\"";
        assertThat(ApprovalFlowDiagnosticLog.parseConstraintName(msg)).isEqualTo("user_decryption_allowed_pkey");
    }

    @Test
    void parseConstraintName_findsGenericConstraint() {
        String msg = "some text constraint \"fk_foo\"";
        assertThat(ApprovalFlowDiagnosticLog.parseConstraintName(msg)).isEqualTo("fk_foo");
    }
}
