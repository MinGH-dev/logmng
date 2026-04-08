package com.logmng.testsupport;

import com.logmng.service.HrSyncPocService;
import org.h2.tools.SimpleResultSet;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test double for HR Sync PoC read paths: no writes; programmable snapshot/employee reads.
 */
public class StubJdbcTemplateForHrSyncPoc extends JdbcTemplate {

    private long countResult = 0L;
    private boolean failGlobalCount;
    private boolean failSnapshotCount;

    private boolean failListSnapshots;
    private List<SnapshotRow> snapshotRows = List.of();

    private final Map<String, Long> countBySnapshotId = new HashMap<>();
    private final Map<String, List<EmployeeRow>> employeesBySnapshotId = new HashMap<>();

    private int queryForObjectNoArgCalls;
    private int queryForObjectSnapshotCountCalls;

    private List<ReplicaDeptRow> replicaDeptRows = List.of();
    private long replicaUsersCount;
    private List<ReplicaUserRow> replicaUsersPageRows = List.of();

    public void setCountResult(long countResult) {
        this.countResult = countResult;
    }

    public void setFailGlobalCount(boolean failGlobalCount) {
        this.failGlobalCount = failGlobalCount;
    }

    public void setFailSnapshotCount(boolean failSnapshotCount) {
        this.failSnapshotCount = failSnapshotCount;
    }

    public void setFailListSnapshots(boolean failListSnapshots) {
        this.failListSnapshots = failListSnapshots;
    }

    public void setSnapshotRows(List<SnapshotRow> snapshotRows) {
        this.snapshotRows = snapshotRows == null ? List.of() : List.copyOf(snapshotRows);
    }

    public void setCountForSnapshot(String snapshotId, long count) {
        countBySnapshotId.put(snapshotId, count);
    }

    public void setEmployeesForSnapshot(String snapshotId, List<EmployeeRow> rows) {
        employeesBySnapshotId.put(snapshotId, rows == null ? List.of() : new ArrayList<>(rows));
    }

    public int getQueryForObjectNoArgCalls() {
        return queryForObjectNoArgCalls;
    }

    public int getQueryForObjectSnapshotCountCalls() {
        return queryForObjectSnapshotCountCalls;
    }

    public void resetStats() {
        queryForObjectNoArgCalls = 0;
        queryForObjectSnapshotCountCalls = 0;
    }

    public void setReplicaDeptRows(List<ReplicaDeptRow> replicaDeptRows) {
        this.replicaDeptRows = replicaDeptRows == null ? List.of() : List.copyOf(replicaDeptRows);
    }

    /** Programmed total and page rows for {@link HrSyncPocService#loadReplicaUsersPage}. */
    public void setReplicaUsersQueryResults(long total, List<ReplicaUserRow> pageRows) {
        this.replicaUsersCount = total;
        this.replicaUsersPageRows = pageRows == null ? List.of() : List.copyOf(pageRows);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
        queryForObjectNoArgCalls++;
        if (failGlobalCount) {
            throw new DataRetrievalFailureException("simulated ext_employee count failure");
        }
        if (!HrSyncPocService.SQL_COUNT_EXT_EMPLOYEE_GLOBAL.equals(sql)) {
            throw new IllegalArgumentException("unexpected sql: " + sql);
        }
        if (requiredType != Long.class) {
            throw new IllegalArgumentException("expected Long");
        }
        @SuppressWarnings("unchecked")
        T v = (T) Long.valueOf(countResult);
        return v;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) throws DataAccessException {
        if (HrSyncPocService.SQL_COUNT_BY_SNAPSHOT.equals(sql)
                && args != null
                && args.length == 1
                && requiredType == Long.class) {
            queryForObjectSnapshotCountCalls++;
            if (failSnapshotCount) {
                throw new DataRetrievalFailureException("simulated snapshot count failure");
            }
            String sid = String.valueOf(args[0]);
            Long c = countBySnapshotId.get(sid);
            if (c == null) {
                throw new IllegalStateException("count not programmed for snapshot: " + sid);
            }
            @SuppressWarnings("unchecked")
            T v = (T) c;
            return v;
        }
        if (sql != null
                && sql.startsWith("SELECT COUNT(*) FROM ext_employee e WHERE")
                && requiredType == Long.class) {
            @SuppressWarnings("unchecked")
            T v = (T) Long.valueOf(replicaUsersCount);
            return v;
        }
        throw new IllegalArgumentException("unexpected queryForObject: " + sql);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
        if (!HrSyncPocService.SQL_LIST_SNAPSHOTS.equals(sql)) {
            throw new IllegalArgumentException("unexpected query: " + sql);
        }
        if (failListSnapshots) {
            throw new RuntimeException("simulated snapshots list failure");
        }
        List<T> out = new ArrayList<>();
        for (int i = 0; i < snapshotRows.size(); i++) {
            SnapshotRow r = snapshotRows.get(i);
            try {
                ResultSet rs = snapshotRowToResultSet(r);
                if (!rs.next()) {
                    throw new SQLException("expected one row in SimpleResultSet");
                }
                out.add(rowMapper.mapRow(rs, i));
            } catch (SQLException e) {
                throw new DataAccessException("mapRow failed", e) {};
            }
        }
        return out;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        if (HrSyncPocService.SQL_REPLICA_DEPARTMENTS.equals(sql)) {
            List<T> out = new ArrayList<>();
            for (int i = 0; i < replicaDeptRows.size(); i++) {
                ReplicaDeptRow r = replicaDeptRows.get(i);
                try {
                    ResultSet rs = replicaDeptToResultSet(r);
                    if (!rs.next()) {
                        throw new SQLException("expected one row in SimpleResultSet");
                    }
                    out.add(rowMapper.mapRow(rs, i));
                } catch (SQLException e) {
                    throw new DataAccessException("mapRow failed", e) {};
                }
            }
            return out;
        }
        if (sql != null
                && sql.contains("SELECT e.external_employee_id")
                && sql.contains("FROM ext_employee e")
                && args != null
                && args.length >= 3) {
            int limit = ((Number) args[args.length - 2]).intValue();
            int offset = ((Number) args[args.length - 1]).intValue();
            List<T> out = new ArrayList<>();
            int to = Math.min(offset + limit, replicaUsersPageRows.size());
            for (int i = offset; i < to; i++) {
                try {
                    ResultSet rs = replicaUserRowToResultSet(replicaUsersPageRows.get(i));
                    if (!rs.next()) {
                        throw new SQLException("expected one row in SimpleResultSet");
                    }
                    out.add(rowMapper.mapRow(rs, i - offset));
                } catch (SQLException e) {
                    throw new DataAccessException("mapRow failed", e) {};
                }
            }
            return out;
        }
        if (!HrSyncPocService.SQL_EMPLOYEES_PAGE.equals(sql) || args == null || args.length != 3) {
            throw new IllegalArgumentException("unexpected query: " + sql);
        }
        String snapshotId = String.valueOf(args[0]);
        int limit = ((Number) args[1]).intValue();
        int offset = ((Number) args[2]).intValue();
        List<EmployeeRow> all = employeesBySnapshotId.getOrDefault(snapshotId, List.of());
        int to = Math.min(offset + limit, all.size());
        List<T> out = new ArrayList<>();
        for (int i = offset; i < to; i++) {
            try {
                ResultSet rs = employeeRowToResultSet(all.get(i));
                if (!rs.next()) {
                    throw new SQLException("expected one row in SimpleResultSet");
                }
                out.add(rowMapper.mapRow(rs, i - offset));
            } catch (SQLException e) {
                throw new DataAccessException("mapRow failed", e) {};
            }
        }
        return out;
    }

    private static ResultSet snapshotRowToResultSet(SnapshotRow r) throws SQLException {
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("snapshot_id", Types.VARCHAR, 128, 0);
        rs.addColumn("employee_count", Types.BIGINT, 20, 0);
        rs.addColumn("max_imported_at", Types.TIMESTAMP, 29, 0);
        rs.addRow(r.snapshotId, r.employeeCount, r.maxImportedAt);
        return rs;
    }

    private static ResultSet replicaDeptToResultSet(ReplicaDeptRow r) throws SQLException {
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("external_department_id", Types.VARCHAR, 256, 0);
        rs.addColumn("parent_external_department_id", Types.VARCHAR, 256, 0);
        rs.addColumn("name", Types.VARCHAR, 500, 0);
        rs.addRow(r.externalDepartmentId, r.parentExternalDepartmentId, r.name);
        return rs;
    }

    private static ResultSet replicaUserRowToResultSet(ReplicaUserRow r) throws SQLException {
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("external_employee_id", Types.VARCHAR, 256, 0);
        rs.addColumn("employee_number", Types.VARCHAR, 100, 0);
        rs.addColumn("display_name", Types.VARCHAR, 500, 0);
        rs.addColumn("job_title", Types.VARCHAR, 200, 0);
        rs.addColumn("external_department_id", Types.VARCHAR, 256, 0);
        rs.addColumn("department_name", Types.VARCHAR, 500, 0);
        rs.addColumn("is_active", Types.BOOLEAN, 1, 0);
        rs.addColumn("snapshot_id", Types.VARCHAR, 128, 0);
        rs.addRow(
                r.externalEmployeeId,
                r.employeeNumber,
                r.displayName,
                r.jobTitle,
                r.externalDepartmentId,
                r.departmentName,
                r.active,
                r.snapshotId);
        return rs;
    }

    private static ResultSet employeeRowToResultSet(EmployeeRow r) throws SQLException {
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("display_name", Types.VARCHAR, 500, 0);
        rs.addColumn("job_title", Types.VARCHAR, 200, 0);
        rs.addColumn("external_department_id", Types.VARCHAR, 256, 0);
        rs.addColumn("department_name", Types.VARCHAR, 500, 0);
        rs.addColumn("is_active", Types.BOOLEAN, 1, 0);
        rs.addColumn("employee_number", Types.VARCHAR, 100, 0);
        rs.addRow(r.displayName, r.jobTitle, r.externalDepartmentId, r.departmentName, r.active, r.employeeNumber);
        return rs;
    }

    @Override
    public int update(String sql) {
        throw new AssertionError("HR Sync PoC must not write: " + sql);
    }

    /** Row for {@link HrSyncPocService#SQL_LIST_SNAPSHOTS}. */
    public static final class SnapshotRow {
        public final String snapshotId;
        public final long employeeCount;
        public final Timestamp maxImportedAt;

        public SnapshotRow(String snapshotId, long employeeCount, Timestamp maxImportedAt) {
            this.snapshotId = snapshotId;
            this.employeeCount = employeeCount;
            this.maxImportedAt = maxImportedAt;
        }
    }

    /** Row for {@link HrSyncPocService#SQL_REPLICA_DEPARTMENTS}. */
    public static final class ReplicaDeptRow {
        public final String externalDepartmentId;
        public final String parentExternalDepartmentId;
        public final String name;

        public ReplicaDeptRow(String externalDepartmentId, String parentExternalDepartmentId, String name) {
            this.externalDepartmentId = externalDepartmentId;
            this.parentExternalDepartmentId = parentExternalDepartmentId;
            this.name = name;
        }
    }

    /** Row for replica-users page query mapping. */
    public static final class ReplicaUserRow {
        public final String externalEmployeeId;
        public final String employeeNumber;
        public final String displayName;
        public final String jobTitle;
        public final String externalDepartmentId;
        public final String departmentName;
        public final boolean active;
        public final String snapshotId;

        public ReplicaUserRow(
                String externalEmployeeId,
                String employeeNumber,
                String displayName,
                String jobTitle,
                String externalDepartmentId,
                String departmentName,
                boolean active,
                String snapshotId) {
            this.externalEmployeeId = externalEmployeeId;
            this.employeeNumber = employeeNumber;
            this.displayName = displayName;
            this.jobTitle = jobTitle;
            this.externalDepartmentId = externalDepartmentId;
            this.departmentName = departmentName;
            this.active = active;
            this.snapshotId = snapshotId;
        }
    }

    /** Row for {@link HrSyncPocService#SQL_EMPLOYEES_PAGE} mapping. */
    public static final class EmployeeRow {
        public final String displayName;
        public final String jobTitle;
        public final String externalDepartmentId;
        public final String departmentName;
        public final boolean active;
        public final String employeeNumber;

        public EmployeeRow(
                String displayName,
                String jobTitle,
                String externalDepartmentId,
                String departmentName,
                boolean active,
                String employeeNumber) {
            this.displayName = displayName;
            this.jobTitle = jobTitle;
            this.externalDepartmentId = externalDepartmentId;
            this.departmentName = departmentName;
            this.active = active;
            this.employeeNumber = employeeNumber;
        }
    }
}
