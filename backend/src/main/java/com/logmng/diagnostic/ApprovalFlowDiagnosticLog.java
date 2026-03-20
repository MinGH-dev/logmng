package com.logmng.diagnostic;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gated diagnostics for search-history approval flow. All entry points check {@code enabled} first;
 * logs use logger {@code com.logmng.diagnostic.approval} at INFO when enabled (no hot-path spam when false).
 */
public final class ApprovalFlowDiagnosticLog {

    private static final Logger LOG = LoggerFactory.getLogger("com.logmng.diagnostic.approval");

    private static final Pattern CONSTRAINT_DOUBLE_QUOTE = Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern CONSTRAINT_UNIQUE = Pattern.compile("unique constraint \"([^\"]+)\"");

    private ApprovalFlowDiagnosticLog() {
    }

    public static void info(boolean enabled, long searchHistoryId, Long approverUserId, String phase, String detail) {
        if (!enabled) {
            return;
        }
        LOG.info("[diag-approval] searchHistoryId={} approverUserId={} phase={} {}",
                searchHistoryId, approverUserId != null ? approverUserId : "", phase, detail != null ? detail : "");
    }

    /**
     * Short DEBUG lines (only if logger DEBUG enabled and flag true — avoid unconditional DEBUG on hot paths).
     */
    public static void debug(boolean enabled, long searchHistoryId, String phase, String detail) {
        if (!enabled || !LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("[diag-approval] searchHistoryId={} phase={} {}", searchHistoryId, phase, detail != null ? detail : "");
    }

    public static void logSqlException(boolean enabled, long searchHistoryId, String phase, SQLException e) {
        if (!enabled) {
            return;
        }
        doLogSqlException(searchHistoryId, phase, e);
    }

    /**
     * When {@code searchHistoryId} is null (non–search-history callers), use {@code -1} for searchHistoryId field and put context in phase detail.
     */
    public static void logSqlException(boolean enabled, Long searchHistoryId, Long userId, String screen, String phase, SQLException e) {
        if (!enabled) {
            return;
        }
        long sid = searchHistoryId != null ? searchHistoryId : -1L;
        String ctx = phase + " userId=" + (userId != null ? userId : "") + " screen=" + (screen != null ? screen : "");
        doLogSqlException(sid, ctx, e);
    }

    private static void doLogSqlException(long searchHistoryId, String phase, SQLException e) {
        String sqlState = e.getSQLState() != null ? e.getSQLState() : "";
        int errorCode = e.getErrorCode();
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String constraint = parseConstraintName(msg);
        String pgSqlState = sqlState;
        if (e instanceof PSQLException) {
            PSQLException pg = (PSQLException) e;
            ServerErrorMessage sem = pg.getServerErrorMessage();
            if (sem != null) {
                if (sem.getSQLState() != null && !sem.getSQLState().isEmpty()) {
                    pgSqlState = sem.getSQLState();
                }
                if (constraint == null && sem.getConstraint() != null) {
                    constraint = sem.getConstraint();
                }
            }
        }
        LOG.info("[diag-approval] searchHistoryId={} phase={} SQLState={} jdbcErrorCode={} pgSqlState={} constraint={}",
                searchHistoryId, phase, sqlState, errorCode, pgSqlState, constraint != null ? constraint : "");
        if (LOG.isDebugEnabled()) {
            LOG.debug("[diag-approval] searchHistoryId={} phase={} exceptionClass={} message={}",
                    searchHistoryId, phase, e.getClass().getName(), truncate(msg, 500));
        }
    }

    public static void controllerThrowable(boolean enabled, long searchHistoryId, Long approverUserId, Throwable t) {
        if (!enabled || t == null) {
            return;
        }
        LOG.info("[diag-approval] searchHistoryId={} approverUserId={} phase=CONTROLLER_THROWABLE exceptionClass={} message={}",
                searchHistoryId, approverUserId != null ? approverUserId : "",
                t.getClass().getName(), truncate(t.getMessage(), 500));
        if (LOG.isDebugEnabled()) {
            Throwable c = t.getCause();
            LOG.debug("[diag-approval] searchHistoryId={} phase=CONTROLLER_THROWABLE causeClass={} causeMessage={}",
                    searchHistoryId, c != null ? c.getClass().getName() : "null", c != null ? truncate(c.getMessage(), 300) : "");
        }
    }

    static String parseConstraintName(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        Matcher m = CONSTRAINT_UNIQUE.matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        m = CONSTRAINT_DOUBLE_QUOTE.matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
