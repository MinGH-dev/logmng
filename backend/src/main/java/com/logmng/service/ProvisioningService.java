package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.constants.ActivityActionType;
import com.logmng.dto.request.ExternalDepartmentSearchRequest;
import com.logmng.dto.request.ExternalEmployeeSearchRequest;
import com.logmng.dto.request.ProvisionFromExternalEmployeeRequest;
import com.logmng.dto.response.ExternalDepartmentItemResponse;
import com.logmng.dto.response.ExternalDepartmentSearchResult;
import com.logmng.dto.response.ExternalEmployeeItemResponse;
import com.logmng.dto.response.ExternalEmployeeSearchResult;
import com.logmng.dto.response.PaginationResponse;
import com.logmng.dto.response.ProvisionUserResultResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.ChangeReasonValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin provisioning: search ext_* and create app_user + app_user_external_identity.
 */
@Service
public class ProvisioningService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Logger diagnosticLog = LoggerFactory.getLogger("com.logmng.diagnostic.provisioningConsistency");

    private final DataSource dataSource;
    private final AuthProperties authProperties;
    private final UserActivityLogService userActivityLogService;
    private final boolean diagnosticProvisioningConsistency;

    public ProvisioningService(DataSource dataSource, AuthProperties authProperties,
                               @Autowired(required = false) UserActivityLogService userActivityLogService,
                               @Value("${app.diagnostic.provisioning-consistency:false}") boolean diagnosticProvisioningConsistency) {
        this.dataSource = dataSource;
        this.authProperties = authProperties;
        this.userActivityLogService = userActivityLogService;
        this.diagnosticProvisioningConsistency = diagnosticProvisioningConsistency;
    }

    public ExternalEmployeeSearchResult searchExternalEmployees(ExternalEmployeeSearchRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? Math.min(req.getPageSize(), MAX_PAGE_SIZE) : 20;
        int offset = (page - 1) * pageSize;

        String fromJoin = "FROM ext_employee e LEFT JOIN ext_department d ON e.source_system = d.source_system "
                + "AND e.external_department_id = d.external_department_id "
                + "LEFT JOIN app_user_external_identity m ON m.source_system = e.source_system "
                + "AND m.external_employee_id = e.external_employee_id "
                + "LEFT JOIN app_user u ON u.id = m.app_user_id AND u.deleted_at IS NULL";
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (StringUtils.hasText(req.getSourceSystem())) {
            where.append(" AND e.source_system = ?");
            params.add(req.getSourceSystem().trim());
        }
        String employeeNumberTerm = req.getEmployeeNumber() == null ? "" : req.getEmployeeNumber().trim();
        if (StringUtils.hasText(employeeNumberTerm)) {
            where.append(" AND e.employee_number LIKE ?");
            params.add(employeeNumberTerm + "%");
        }
        if (StringUtils.hasText(req.getExternalDepartmentId())) {
            where.append(" AND e.external_department_id = ?");
            params.add(req.getExternalDepartmentId().trim());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            where.append(" AND e.display_name ILIKE ?");
            params.add("%" + req.getKeyword().trim() + "%");
        }
        if (StringUtils.hasText(req.getDepartmentName())) {
            where.append(" AND d.name ILIKE ?");
            params.add("%" + req.getDepartmentName().trim() + "%");
        }

        long total;
        try {
            total = countJoined(fromJoin, where.toString(), params);
        } catch (Exception e) {
            throw new IllegalStateException("ext_employee count failed", e);
        }

        String sql = "SELECT e.source_system, e.external_employee_id, e.employee_number, e.display_name, e.job_title, "
                + "e.external_department_id, d.name AS department_name, "
                + "u.id AS provisioned_app_user_id, u.username AS provisioned_username "
                + fromJoin + " " + where
                + " ORDER BY e.employee_number NULLS LAST, e.external_employee_id LIMIT ? OFFSET ?";
        List<ExternalEmployeeItemResponse> items = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            ps.setInt(i++, pageSize);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long provisionedAppUserId = rs.getObject("provisioned_app_user_id") != null
                            ? rs.getLong("provisioned_app_user_id") : null;
                    boolean provisioned = provisionedAppUserId != null;
                    String extEmpId = rs.getString("external_employee_id");
                    String empNumRaw = rs.getString("employee_number");
                    if (diagnosticProvisioningConsistency) {
                        diagnosticLog.debug(
                                "[diag-provision] ext search row: sourceSystem={} externalEmployeeId={} "
                                        + "employeeNumberRaw={} provisioned={} provisionedAppUserId={}",
                                rs.getString("source_system"),
                                extEmpId,
                                empNumRaw,
                                provisioned,
                                provisionedAppUserId);
                    }
                    items.add(new ExternalEmployeeItemResponse(
                            extEmpId,
                            rs.getString("source_system"),
                            empNumRaw,
                            rs.getString("display_name"),
                            rs.getString("external_department_id"),
                            rs.getString("job_title"),
                            rs.getString("department_name"),
                            provisioned,
                            rs.getString("provisioned_username"),
                            provisionedAppUserId));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("ext_employee search failed", e);
        }

        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        PaginationResponse pagination = new PaginationResponse(page, totalPages, total);
        return new ExternalEmployeeSearchResult(items, pagination);
    }

    public ExternalDepartmentSearchResult searchExternalDepartments(ExternalDepartmentSearchRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? Math.min(req.getPageSize(), MAX_PAGE_SIZE) : 20;
        int offset = (page - 1) * pageSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (StringUtils.hasText(req.getSourceSystem())) {
            where.append(" AND source_system = ?");
            params.add(req.getSourceSystem().trim());
        }
        if (StringUtils.hasText(req.getExternalDepartmentId())) {
            where.append(" AND external_department_id = ?");
            params.add(req.getExternalDepartmentId().trim());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            where.append(" AND name ILIKE ?");
            params.add("%" + req.getKeyword().trim() + "%");
        }

        long total;
        try {
            total = count("ext_department", where.toString(), params);
        } catch (Exception e) {
            throw new IllegalStateException("ext_department count failed", e);
        }

        String sql = "SELECT source_system, external_department_id, name, parent_external_department_id "
                + "FROM ext_department " + where
                + " ORDER BY external_department_id LIMIT ? OFFSET ?";
        List<ExternalDepartmentItemResponse> items = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            ps.setInt(i++, pageSize);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ExternalDepartmentItemResponse(
                            rs.getString("external_department_id"),
                            rs.getString("source_system"),
                            rs.getString("name"),
                            rs.getString("parent_external_department_id")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("ext_department search failed", e);
        }

        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        PaginationResponse pagination = new PaginationResponse(page, totalPages, total);
        return new ExternalDepartmentSearchResult(items, pagination);
    }

    private long count(String table, String where, List<Object> params) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table + " " + where;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Count with same FROM/JOIN/WHERE as the list query for ext_employee search. */
    private long countJoined(String fromJoin, String where, List<Object> params) throws Exception {
        String sql = "SELECT COUNT(*) " + fromJoin + " " + where;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * @param actorUsername login username for activity log (required for audit row)
     */
    public ProvisionUserResultResponse provisionFromExternalEmployee(ProvisionFromExternalEmployeeRequest req,
                                                                     String actorUsername,
                                                                     String clientIp,
                                                                     String userAgent) {
        if (!StringUtils.hasText(req.getExternalEmployeeId())) {
            throw CustomException.badRequest("externalEmployeeId는 필수입니다.", "INVALID_INPUT");
        }
        String validatedReason = ChangeReasonValidator.requireValidChangeReason(req.getChangeReason());
        if (actorUsername == null || actorUsername.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        String extId = req.getExternalEmployeeId().trim();
        String sourceSystem = StringUtils.hasText(req.getSourceSystem())
                ? req.getSourceSystem().trim()
                : authProperties.getProvisioning().getDefaultSourceSystem();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ExistingMappedUser existing = findExistingMappedUser(conn, sourceSystem, extId);
                if (existing != null) {
                    conn.rollback();
                    Map<String, Object> details = new LinkedHashMap<>();
                    if (existing.username != null && !existing.username.isBlank()) {
                        details.put("existingUsername", existing.username.trim());
                    }
                    details.put("existingAppUserId", existing.appUserId);
                    throw CustomException.conflict("이미 등록된 외부 직원 키입니다.", "EXTERNAL_IDENTITY_CONFLICT",
                            details);
                }

                String displayName;
                String employeeNumber;
                String externalDepartmentId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT display_name, employee_number, external_department_id FROM ext_employee "
                                + "WHERE source_system = ? AND external_employee_id = ?")) {
                    ps.setString(1, sourceSystem);
                    ps.setString(2, extId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw CustomException.notFound("외부 직원 행을 찾을 수 없습니다.", "EXT_EMPLOYEE_NOT_FOUND");
                        }
                        displayName = rs.getString("display_name");
                        employeeNumber = rs.getString("employee_number");
                        externalDepartmentId = rs.getString("external_department_id");
                    }
                }

                String employeeNumberTrimmed = StringUtils.hasText(employeeNumber) ? employeeNumber.trim() : null;
                if (employeeNumberTrimmed != null) {
                    AppUserEmployeeNumberUniqueness.ensureAvailableForActiveUser(conn, employeeNumberTrimmed);
                }

                String departmentCode = null;
                if (StringUtils.hasText(req.getDepartmentCode())) {
                    departmentCode = req.getDepartmentCode().trim();
                    assertDepartmentExists(conn, departmentCode);
                } else if (StringUtils.hasText(externalDepartmentId)) {
                    try (PreparedStatement linkPs = conn.prepareStatement(
                            "SELECT department_code FROM department_org_link WHERE source_system = ? AND external_department_id = ?")) {
                        linkPs.setString(1, sourceSystem);
                        linkPs.setString(2, externalDepartmentId.trim());
                        try (ResultSet rs = linkPs.executeQuery()) {
                            if (rs.next()) {
                                departmentCode = rs.getString("department_code");
                                if (departmentCode != null) {
                                    departmentCode = departmentCode.trim();
                                }
                                if (StringUtils.hasText(departmentCode)) {
                                    assertDepartmentExists(conn, departmentCode);
                                } else {
                                    departmentCode = null;
                                }
                            }
                        }
                    }
                }

                String username = allocateUsername(conn, employeeNumberTrimmed, extId);
                String passwordPlaceholder = "AD_PROVISIONED_" + UUID.randomUUID();

                long newId;
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO app_user (username, password_hash, role, department_code, name, employee_number, is_system_admin) "
                                + "VALUES (?, ?, 'USER', ?, ?, ?, false)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, username);
                    ins.setString(2, passwordPlaceholder);
                    if (departmentCode != null) {
                        ins.setString(3, departmentCode);
                    } else {
                        ins.setNull(3, Types.VARCHAR);
                    }
                    if (displayName != null && !displayName.isBlank()) {
                        ins.setString(4, displayName);
                    } else {
                        ins.setNull(4, Types.VARCHAR);
                    }
                    if (employeeNumberTrimmed != null) {
                        ins.setString(5, employeeNumberTrimmed);
                    } else {
                        ins.setNull(5, Types.VARCHAR);
                    }
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new IllegalStateException("no generated key for app_user");
                        }
                        newId = keys.getLong(1);
                    }
                }

                try (PreparedStatement map = conn.prepareStatement(
                        "INSERT INTO app_user_external_identity (app_user_id, source_system, external_employee_id) VALUES (?, ?, ?)")) {
                    map.setLong(1, newId);
                    map.setString(2, sourceSystem);
                    map.setString(3, extId);
                    map.executeUpdate();
                }

                conn.commit();
                if (diagnosticProvisioningConsistency) {
                    diagnosticLog.debug(
                            "[diag-provision] provision success: newAppUserId={} username={} employeeNumber={} "
                                    + "externalEmployeeId={} sourceSystem={}",
                            newId,
                            username,
                            employeeNumberTrimmed,
                            extId,
                            sourceSystem);
                }
                emitUserCreateIfConfigured(actorUsername.trim(), validatedReason, newId, employeeNumberTrimmed,
                        username, clientIp, userAgent);
                return new ProvisionUserResultResponse(newId, username, employeeNumberTrimmed);
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("provision failed", e);
        }
    }

    private void emitUserCreateIfConfigured(String actorUsername, String changeReason, long newUserId,
                                           String employeeNumberOrNull, String username, String clientIp, String userAgent) {
        if (userActivityLogService == null) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changeReason", changeReason);
        detail.put("targetUserId", newUserId);
        if (employeeNumberOrNull != null && !employeeNumberOrNull.isBlank()) {
            detail.put("employeeNumber", employeeNumberOrNull.trim());
        }
        if (username != null && !username.isBlank()) {
            detail.put("username", username.trim());
        }
        userActivityLogService.saveActivityLog(
                actorUsername,
                actorUsername,
                ActivityActionType.USER_CREATE.getCode(),
                detail,
                clientIp,
                userAgent,
                "POST",
                "/api/provisioning/users/from-external-employee",
                null,
                200,
                null,
                true,
                null);
    }

    private static void assertDepartmentExists(Connection conn, String departmentCode) throws Exception {
        try (PreparedStatement chk = conn.prepareStatement("SELECT 1 FROM department WHERE code = ?")) {
            chk.setString(1, departmentCode);
            try (ResultSet rs = chk.executeQuery()) {
                if (!rs.next()) {
                    conn.rollback();
                    throw CustomException.badRequest("유효하지 않은 부서 코드입니다.", "INVALID_INPUT");
                }
            }
        }
    }

    private static final class ExistingMappedUser {
        final long appUserId;
        final String username;

        ExistingMappedUser(long appUserId, String username) {
            this.appUserId = appUserId;
            this.username = username;
        }
    }

    /**
     * Returns app user linked to the external key, if any (for conflict payload and duplicate checks).
     */
    private static ExistingMappedUser findExistingMappedUser(Connection conn, String sourceSystem, String externalEmployeeId)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT m.app_user_id, u.username FROM app_user_external_identity m "
                        + "INNER JOIN app_user u ON u.id = m.app_user_id AND u.deleted_at IS NULL "
                        + "WHERE m.source_system = ? AND m.external_employee_id = ?")) {
            ps.setString(1, sourceSystem);
            ps.setString(2, externalEmployeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ExistingMappedUser(rs.getLong("app_user_id"), rs.getString("username"));
            }
        }
    }

    private static String allocateUsername(Connection conn, String employeeNumberTrimmedOrNull, String externalEmployeeId) throws Exception {
        String base = "emp";
        if (StringUtils.hasText(employeeNumberTrimmedOrNull)) {
            base = "emp_" + employeeNumberTrimmedOrNull.replaceAll("[^a-zA-Z0-9_]", "_");
        } else {
            base = "emp_" + externalEmployeeId.replaceAll("[^a-zA-Z0-9_]", "_");
        }
        if (base.length() > 95) {
            base = base.substring(0, 95);
        }
        String candidate = base.toLowerCase(Locale.ROOT);
        int suffix = 0;
        while (true) {
            String tryName = suffix == 0 ? candidate : (candidate + "_" + suffix);
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM app_user WHERE username = ?")) {
                ps.setString(1, tryName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return tryName;
                    }
                }
            }
            suffix++;
            if (suffix > 1000) {
                return candidate + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
        }
    }
}
