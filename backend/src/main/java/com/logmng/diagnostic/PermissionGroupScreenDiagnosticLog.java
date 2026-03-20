package com.logmng.diagnostic;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Gated DEBUG diagnostics for permission-group management screen / {@code GET /api/permission-groups}.
 * Enable {@code app.diagnostic.permission-group-screen} and set logger {@code com.logmng.diagnostic.permissionGroupScreen} to DEBUG for reproduction.
 * No PII in messages (numeric user id only where noted).
 */
public final class PermissionGroupScreenDiagnosticLog {

    private static final Logger LOG = LoggerFactory.getLogger("com.logmng.diagnostic.permissionGroupScreen");

    private PermissionGroupScreenDiagnosticLog() {
    }

    public static void debug(boolean enabled, String event, String detail) {
        if (!enabled || !LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("[diag-pg-screen] event={} {}", event, detail != null ? detail : "");
    }

    /**
     * Sanitized SQLException summary at DEBUG (SQLState, JDBC code, truncated server message). No bind values.
     */
    public static void sqlException(boolean enabled, String phase, Long permissionGroupId, SQLException e) {
        if (!enabled || e == null || !LOG.isDebugEnabled()) {
            return;
        }
        String sqlState = e.getSQLState() != null ? e.getSQLState() : "";
        int errorCode = e.getErrorCode();
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String constraint = ApprovalFlowDiagnosticLog.parseConstraintName(msg);
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
        LOG.debug("[diag-pg-screen] phase={} permissionGroupId={} SQLState={} jdbcErrorCode={} pgSqlState={} constraint={} exceptionClass={} message={}",
                phase != null ? phase : "",
                permissionGroupId != null ? permissionGroupId : "",
                sqlState,
                errorCode,
                pgSqlState,
                constraint != null ? constraint : "",
                e.getClass().getName(),
                truncate(msg, 400));
    }

    /**
     * Screen interceptor denied access to a permission-groups API path (403 path). userId = session numeric id only.
     */
    public static void screenAccessDenyPermissionGroups(boolean enabled, String path, List<String> requiredScreens,
                                                        Long userId, String denyReason) {
        if (!enabled || !LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("[diag-pg-screen] event=screen_access_deny path={} requiredScreens={} userId={} reason={}",
                path != null ? path : "",
                requiredScreens != null ? requiredScreens : List.of(),
                userId != null ? userId : "",
                denyReason != null ? denyReason : "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
