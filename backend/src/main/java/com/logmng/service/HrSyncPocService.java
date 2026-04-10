package com.logmng.service;

import com.logmng.dto.response.HrSyncPocClassificationCounts;
import com.logmng.dto.response.HrSyncPocEmployeeRow;
import com.logmng.dto.response.HrSyncPocEmployeesPageResponse;
import com.logmng.dto.response.HrSyncPocPreviewResponse;
import com.logmng.dto.response.HrSyncPocReplicaDepartmentTreeNode;
import com.logmng.dto.response.HrSyncPocReplicaDepartmentTreeResponse;
import com.logmng.dto.response.HrSyncPocReplicaUserRow;
import com.logmng.dto.response.HrSyncPocReplicaUsersPageResponse;
import com.logmng.dto.response.HrSyncPocSnapshotItem;
import com.logmng.dto.response.HrSyncPocSnapshotsResponse;
import com.logmng.dto.response.PaginationResponse;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only PoC: no writes to app_user, permission, or tree tables.
 */
@Service
public class HrSyncPocService {

    private static final Logger log = LoggerFactory.getLogger(HrSyncPocService.class);

    private static final String MESSAGE_CODE_PREVIEW_OK = "HR_SYNC_POC_PREVIEW_OK";

    /** Distinct snapshots with counts (unqualified ext_*; search_path from datasource). */
    public static final String SQL_LIST_SNAPSHOTS = ""
            + "SELECT snapshot_id, COUNT(*)::bigint AS employee_count, MAX(imported_at) AS max_imported_at "
            + "FROM ext_employee WHERE snapshot_id IS NOT NULL "
            + "GROUP BY snapshot_id ORDER BY snapshot_id ASC";

    public static final String SQL_COUNT_BY_SNAPSHOT = "SELECT COUNT(*) FROM ext_employee WHERE snapshot_id = ?";

    public static final String SQL_COUNT_EXT_EMPLOYEE_GLOBAL = "SELECT COUNT(*) FROM ext_employee";

    public static final String SQL_EMPLOYEES_PAGE = ""
            + "SELECT e.display_name, e.job_title, e.external_department_id, d.name AS department_name, "
            + "e.is_active, e.employee_number "
            + "FROM ext_employee e "
            + "LEFT JOIN ext_department d ON d.source_system = e.source_system "
            + "AND d.external_department_id = e.external_department_id "
            + "WHERE e.snapshot_id = ? "
            + "ORDER BY e.display_name NULLS LAST, e.external_employee_id "
            + "LIMIT ? OFFSET ?";

    /**
     * Replica departments for PoC UM tree ({@code ext_department} only), stable sibling order by name.
     */
    public static final String SQL_REPLICA_DEPARTMENTS =
            "SELECT external_department_id, parent_external_department_id, name "
                    + "FROM ext_department WHERE source_system = ? "
                    + "ORDER BY name NULLS LAST, external_department_id";

    private static final Pattern SNAPSHOT_ID_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");

    private static final int MAX_SOURCE_SYSTEM_LEN = 64;
    private static final int MAX_DEPT_KEY_LEN = 256;

    private final JdbcTemplate jdbcTemplate;

    public HrSyncPocService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Build preview response. {@code snapshotId} / {@code ingestRunId} are already validated strings (optional).
     */
    public HrSyncPocPreviewResponse buildPreview(String snapshotId, String ingestRunId) {
        String resolvedSnapshot = "";
        if (StringUtils.hasText(snapshotId)) {
            resolvedSnapshot = snapshotId.trim();
        } else if (StringUtils.hasText(ingestRunId)) {
            resolvedSnapshot = ingestRunId.trim();
        }

        HrSyncPocClassificationCounts counts = loadClassificationStubCounts(resolvedSnapshot);
        String previewId = UUID.randomUUID().toString();

        return new HrSyncPocPreviewResponse(
                previewId,
                resolvedSnapshot,
                counts,
                "AUTO",
                "PLACEHOLDER",
                MESSAGE_CODE_PREVIEW_OK);
    }

    /**
     * SELECT-only against {@code ext_employee}: snapshot-scoped count when {@code resolvedSnapshotForScope}
     * is non-blank ({@link #SQL_COUNT_BY_SNAPSHOT}), else global {@link #SQL_COUNT_EXT_EMPLOYEE_GLOBAL}.
     * On DB/query failures throws {@link CustomException} (no silent all-zero success).
     */
    HrSyncPocClassificationCounts loadClassificationStubCounts(String resolvedSnapshotForScope) {
        boolean scoped = StringUtils.hasText(resolvedSnapshotForScope);
        try {
            Long n =
                    scoped
                            ? jdbcTemplate.queryForObject(
                                    SQL_COUNT_BY_SNAPSHOT,
                                    Long.class,
                                    resolvedSnapshotForScope.trim())
                            : jdbcTemplate.queryForObject(SQL_COUNT_EXT_EMPLOYEE_GLOBAL, Long.class);
            if (n == null || n < 0) {
                return HrSyncPocClassificationCounts.allZeros();
            }
            return HrSyncPocClassificationCounts.stubUnchangedOnly(n);
        } catch (DataAccessException e) {
            log.debug("HR Sync PoC preview count failed ({})", e.toString());
            throw CustomException.serviceUnavailable(
                    "HR Sync PoC 미리보기 집계를 조회할 수 없습니다.", "HR_SYNC_POC_PREVIEW_FAILED");
        }
    }

    /**
     * Distinct non-null snapshot_id rows with aggregate metadata.
     */
    public HrSyncPocSnapshotsResponse loadSnapshots() {
        try {
            List<HrSyncPocSnapshotItem> rows = jdbcTemplate.query(
                    SQL_LIST_SNAPSHOTS,
                    (rs, rowNum) -> {
                        String sid = rs.getString("snapshot_id");
                        long cnt = rs.getLong("employee_count");
                        Timestamp maxTs = rs.getTimestamp("max_imported_at");
                        int employeeCount = cnt > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cnt;
                        return new HrSyncPocSnapshotItem(
                                sid,
                                deriveSnapshotLabel(sid),
                                employeeCount,
                                formatImportedAtIso(maxTs));
                    });
            return new HrSyncPocSnapshotsResponse(rows);
        } catch (Exception e) {
            log.debug("HR Sync PoC snapshots list skipped ({})", e.toString());
            return new HrSyncPocSnapshotsResponse(new ArrayList<>());
        }
    }

    /**
     * Paginated replica employees for a snapshot; throws {@link CustomException} NOT_FOUND if snapshot has no rows.
     */
    public HrSyncPocEmployeesPageResponse loadEmployeesPage(String snapshotId, int page, int size) {
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_BY_SNAPSHOT, Long.class, snapshotId);
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            throw CustomException.notFound("해당 스냅샷에 복제 직원 데이터가 없습니다.", "NOT_FOUND");
        }
        int offset = (page - 1) * size;
        List<HrSyncPocEmployeeRow> employees = jdbcTemplate.query(
                SQL_EMPLOYEES_PAGE,
                (rs, rowNum) -> new HrSyncPocEmployeeRow(
                        rs.getString("display_name"),
                        rs.getString("job_title"),
                        rs.getString("external_department_id"),
                        rs.getString("department_name"),
                        rs.getBoolean("is_active"),
                        normalizeEmployeeNumber(rs.getString("employee_number"))),
                snapshotId,
                size,
                offset);
        int totalPages = (int) Math.max(1L, (totalCount + size - 1) / size);
        PaginationResponse pagination = new PaginationResponse(page, totalPages, totalCount);
        return new HrSyncPocEmployeesPageResponse(snapshotId, employees, pagination);
    }

    /**
     * Validate {@code snapshotId} path segment per {@code specs/hr-sync-poc.spec.yaml} §4.4.
     */
    public static void validateSnapshotIdForPath(String rawSnapshotId) {
        if (!StringUtils.hasText(rawSnapshotId)) {
            throw CustomException.badRequest("snapshotId가 필요합니다.", "VALIDATION_ERROR");
        }
        String s = rawSnapshotId.trim();
        if (s.length() > 128) {
            throw CustomException.badRequest("snapshotId 길이가 상한을 초과합니다.", "VALIDATION_ERROR");
        }
        if (!SNAPSHOT_ID_PATH_PATTERN.matcher(s).matches()) {
            throw CustomException.badRequest("snapshotId 형식이 올바르지 않습니다.", "VALIDATION_ERROR");
        }
    }

    public static void validateEmployeePageParams(int page, int size) {
        if (page < 1) {
            throw CustomException.badRequest("page는 1 이상이어야 합니다.", "VALIDATION_ERROR");
        }
        if (size < 1) {
            throw CustomException.badRequest("size는 1 이상이어야 합니다.", "VALIDATION_ERROR");
        }
        if (size > 100) {
            throw CustomException.badRequest("size는 최대 100입니다.", "VALIDATION_ERROR");
        }
    }

    static String deriveSnapshotLabel(String snapshotId) {
        if (!StringUtils.hasText(snapshotId)) {
            return null;
        }
        int i = snapshotId.lastIndexOf('-');
        if (i < 0 || i >= snapshotId.length() - 1) {
            return null;
        }
        String suffix = snapshotId.substring(i + 1);
        if (suffix.length() > 16 || !suffix.matches("[A-Za-z0-9]+")) {
            return null;
        }
        if (suffix.length() == 1) {
            return "PoC sample " + suffix.toUpperCase();
        }
        return "PoC sample " + suffix;
    }

    static String formatImportedAtIso(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toInstant().atOffset(ZoneOffset.UTC).toString();
    }

    /** PoC personnel list: full replica employee_number (no masking). */
    static String normalizeEmployeeNumber(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }

    /**
     * Default {@code HR_SAMPLE}; trim; max length matches {@code ext_department.source_system}.
     */
    public static String normalizePocSourceSystem(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "HR_SAMPLE";
        }
        String s = raw.trim();
        if (s.length() > MAX_SOURCE_SYSTEM_LEN) {
            throw CustomException.badRequest("sourceSystem 길이가 상한을 초과합니다.", "VALIDATION_ERROR");
        }
        return s;
    }

    /** When non-blank, same rules as path {@code snapshotId} (§4.4). */
    public static void validateOptionalSnapshotIdForQuery(String snapshotId) {
        if (!StringUtils.hasText(snapshotId)) {
            return;
        }
        validateSnapshotIdForPath(snapshotId.trim());
    }

    public static void validateOptionalDepartmentKey(String departmentKey) {
        if (!StringUtils.hasText(departmentKey)) {
            return;
        }
        if (departmentKey.trim().length() > MAX_DEPT_KEY_LEN) {
            throw CustomException.badRequest("departmentKey 길이가 상한을 초과합니다.", "VALIDATION_ERROR");
        }
    }

    /**
     * Nested tree: roots = rows with null/blank parent or unknown parent; siblings sorted by name.
     */
    public HrSyncPocReplicaDepartmentTreeResponse loadReplicaDepartmentTree(String sourceSystemRaw) {
        String sourceSystem = normalizePocSourceSystem(sourceSystemRaw);
        List<ExtDeptFlat> rows;
        try {
            rows = jdbcTemplate.query(
                    SQL_REPLICA_DEPARTMENTS,
                    (rs, rowNum) ->
                            new ExtDeptFlat(
                                    rs.getString("external_department_id"),
                                    rs.getString("parent_external_department_id"),
                                    rs.getString("name")),
                    sourceSystem);
        } catch (DataAccessException e) {
            log.debug("HR Sync PoC replica department tree failed ({})", e.toString());
            throw CustomException.serviceUnavailable(
                    "PoC 복제 부서 트리를 조회할 수 없습니다.", "HR_SYNC_POC_PREVIEW_FAILED");
        }
        List<HrSyncPocReplicaDepartmentTreeNode> roots = buildReplicaDepartmentTree(rows);
        return new HrSyncPocReplicaDepartmentTreeResponse(sourceSystem, roots);
    }

    /** Package-private for tests: builds nested tree from flat {@code ext_department} rows. */
    static List<HrSyncPocReplicaDepartmentTreeNode> buildReplicaDepartmentTree(List<ExtDeptFlat> rows) {
        Map<String, HrSyncPocReplicaDepartmentTreeNode> nodes = new LinkedHashMap<>();
        for (ExtDeptFlat r : rows) {
            if (!StringUtils.hasText(r.externalDepartmentId)) {
                continue;
            }
            String key = r.externalDepartmentId.trim();
            HrSyncPocReplicaDepartmentTreeNode n = new HrSyncPocReplicaDepartmentTreeNode();
            n.setDepartmentKey(key);
            n.setParentDepartmentKey(
                    StringUtils.hasText(r.parentExternalDepartmentId) ? r.parentExternalDepartmentId.trim() : null);
            n.setName(r.name);
            n.setSortOrder(0);
            n.setChildren(new ArrayList<>());
            nodes.put(key, n);
        }
        List<HrSyncPocReplicaDepartmentTreeNode> roots = new ArrayList<>();
        for (ExtDeptFlat r : rows) {
            if (!StringUtils.hasText(r.externalDepartmentId)) {
                continue;
            }
            String key = r.externalDepartmentId.trim();
            HrSyncPocReplicaDepartmentTreeNode n = nodes.get(key);
            if (n == null) {
                continue;
            }
            String p = r.parentExternalDepartmentId != null ? r.parentExternalDepartmentId.trim() : "";
            if (!StringUtils.hasText(p) || !nodes.containsKey(p)) {
                roots.add(n);
            } else {
                nodes.get(p).getChildren().add(n);
            }
        }
        Comparator<HrSyncPocReplicaDepartmentTreeNode> cmp =
                Comparator.comparing(
                                (HrSyncPocReplicaDepartmentTreeNode n) -> nullsLastString(n.getName()),
                                String::compareTo)
                        .thenComparing(
                                node ->
                                        node.getDepartmentKey() != null
                                                ? node.getDepartmentKey()
                                                : "",
                                String.CASE_INSENSITIVE_ORDER);
        roots.sort(cmp);
        for (HrSyncPocReplicaDepartmentTreeNode root : roots) {
            sortReplicaTreeChildren(root, cmp);
        }
        return roots;
    }

    private static String nullsLastString(String a) {
        if (a == null) {
            return "\uFFFF";
        }
        return a.toLowerCase(Locale.ROOT);
    }

    private static void sortReplicaTreeChildren(
            HrSyncPocReplicaDepartmentTreeNode node, Comparator<HrSyncPocReplicaDepartmentTreeNode> cmp) {
        node.getChildren().sort(cmp);
        for (HrSyncPocReplicaDepartmentTreeNode c : node.getChildren()) {
            sortReplicaTreeChildren(c, cmp);
        }
    }

    /**
     * Paginated replica users. When {@code snapshotId} filter is set and count is 0 → {@code NOT_FOUND} (§4.6).
     */
    public HrSyncPocReplicaUsersPageResponse loadReplicaUsersPage(
            String sourceSystemRaw,
            String snapshotIdRaw,
            String departmentKeyRaw,
            int page,
            int size) {
        String sourceSystem = normalizePocSourceSystem(sourceSystemRaw);
        validateEmployeePageParams(page, size);
        String snapshotFilter = null;
        if (StringUtils.hasText(snapshotIdRaw)) {
            validateOptionalSnapshotIdForQuery(snapshotIdRaw);
            snapshotFilter = snapshotIdRaw.trim();
        }
        String departmentFilter = null;
        if (StringUtils.hasText(departmentKeyRaw)) {
            validateOptionalDepartmentKey(departmentKeyRaw);
            departmentFilter = departmentKeyRaw.trim();
        }

        StringBuilder where = new StringBuilder("WHERE e.source_system = ?");
        List<Object> args = new ArrayList<>();
        args.add(sourceSystem);
        if (snapshotFilter != null) {
            where.append(" AND e.snapshot_id = ?");
            args.add(snapshotFilter);
        }
        if (departmentFilter != null) {
            where.append(" AND e.external_department_id = ?");
            args.add(departmentFilter);
        }

        String countSql = "SELECT COUNT(*) FROM ext_employee e " + where;
        Long total;
        try {
            total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        } catch (DataAccessException e) {
            log.debug("HR Sync PoC replica users count failed ({})", e.toString());
            throw CustomException.serviceUnavailable(
                    "PoC 복제 사용자 목록을 조회할 수 없습니다.", "HR_SYNC_POC_PREVIEW_FAILED");
        }
        long totalCount = total == null ? 0L : total;

        if (snapshotFilter != null && totalCount == 0L) {
            throw CustomException.notFound("해당 스냅샷에 복제 직원 데이터가 없습니다.", "NOT_FOUND");
        }

        int offset = (page - 1) * size;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);

        String pageSql =
                "SELECT e.external_employee_id, e.employee_number, e.display_name, e.job_title, "
                        + "e.external_department_id, d.name AS department_name, e.is_active, e.snapshot_id "
                        + "FROM ext_employee e "
                        + "LEFT JOIN ext_department d ON d.source_system = e.source_system "
                        + "AND d.external_department_id = e.external_department_id "
                        + where
                        + " ORDER BY e.display_name NULLS LAST, e.external_employee_id LIMIT ? OFFSET ?";

        List<HrSyncPocReplicaUserRow> employees;
        try {
            employees =
                    jdbcTemplate.query(
                            pageSql,
                            (rs, rowNum) ->
                                    new HrSyncPocReplicaUserRow(
                                            rs.getString("external_employee_id"),
                                            normalizeEmployeeNumber(rs.getString("employee_number")),
                                            rs.getString("display_name"),
                                            rs.getString("job_title"),
                                            rs.getString("external_department_id"),
                                            rs.getString("department_name"),
                                            rs.getBoolean("is_active"),
                                            rs.getString("snapshot_id")),
                            pageArgs.toArray());
        } catch (DataAccessException e) {
            log.debug("HR Sync PoC replica users page failed ({})", e.toString());
            throw CustomException.serviceUnavailable(
                    "PoC 복제 사용자 목록을 조회할 수 없습니다.", "HR_SYNC_POC_PREVIEW_FAILED");
        }

        int totalPages = (int) Math.max(1L, (totalCount + size - 1) / size);
        PaginationResponse pagination = new PaginationResponse(page, totalPages, totalCount);
        return new HrSyncPocReplicaUsersPageResponse(
                snapshotFilter, departmentFilter, sourceSystem, employees, pagination);
    }

    static final class ExtDeptFlat {
        final String externalDepartmentId;
        final String parentExternalDepartmentId;
        final String name;

        ExtDeptFlat(String externalDepartmentId, String parentExternalDepartmentId, String name) {
            this.externalDepartmentId = externalDepartmentId;
            this.parentExternalDepartmentId = parentExternalDepartmentId;
            this.name = name;
        }
    }
}
