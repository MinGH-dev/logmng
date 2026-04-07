package com.logmng.service;

import com.logmng.config.AuthProperties;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin provisioning: search ext_* and create app_user + app_user_external_identity.
 */
@Service
public class ProvisioningService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DataSource dataSource;
    private final AuthProperties authProperties;

    public ProvisioningService(DataSource dataSource, AuthProperties authProperties) {
        this.dataSource = dataSource;
        this.authProperties = authProperties;
    }

    public ExternalEmployeeSearchResult searchExternalEmployees(ExternalEmployeeSearchRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? Math.min(req.getPageSize(), MAX_PAGE_SIZE) : 20;
        int offset = (page - 1) * pageSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (StringUtils.hasText(req.getSourceSystem())) {
            where.append(" AND source_system = ?");
            params.add(req.getSourceSystem().trim());
        }
        if (StringUtils.hasText(req.getEmployeeNumber())) {
            where.append(" AND employee_number LIKE ?");
            params.add(req.getEmployeeNumber().trim() + "%");
        }
        if (StringUtils.hasText(req.getExternalDepartmentId())) {
            where.append(" AND external_department_id = ?");
            params.add(req.getExternalDepartmentId().trim());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            where.append(" AND display_name ILIKE ?");
            params.add("%" + req.getKeyword().trim() + "%");
        }

        long total;
        try {
            total = count("ext_employee", where.toString(), params);
        } catch (Exception e) {
            throw new IllegalStateException("ext_employee count failed", e);
        }

        String sql = "SELECT source_system, external_employee_id, employee_number, display_name, job_title, external_department_id "
                + "FROM ext_employee " + where
                + " ORDER BY employee_number NULLS LAST, external_employee_id LIMIT ? OFFSET ?";
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
                    items.add(new ExternalEmployeeItemResponse(
                            rs.getString("external_employee_id"),
                            rs.getString("source_system"),
                            rs.getString("employee_number"),
                            rs.getString("display_name"),
                            rs.getString("external_department_id"),
                            rs.getString("job_title")));
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

    public ProvisionUserResultResponse provisionFromExternalEmployee(ProvisionFromExternalEmployeeRequest req) {
        if (!StringUtils.hasText(req.getExternalEmployeeId())) {
            throw CustomException.badRequest("externalEmployeeId는 필수입니다.", "INVALID_INPUT");
        }
        String extId = req.getExternalEmployeeId().trim();
        String sourceSystem = StringUtils.hasText(req.getSourceSystem())
                ? req.getSourceSystem().trim()
                : authProperties.getProvisioning().getDefaultSourceSystem();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (mappingExists(conn, sourceSystem, extId)) {
                    conn.rollback();
                    throw CustomException.conflict("이미 등록된 외부 직원 키입니다.", "EXTERNAL_IDENTITY_CONFLICT");
                }

                String displayName;
                String employeeNumber;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT display_name, employee_number FROM ext_employee WHERE source_system = ? AND external_employee_id = ?")) {
                    ps.setString(1, sourceSystem);
                    ps.setString(2, extId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw CustomException.notFound("외부 직원 행을 찾을 수 없습니다.", "EXT_EMPLOYEE_NOT_FOUND");
                        }
                        displayName = rs.getString("display_name");
                        employeeNumber = rs.getString("employee_number");
                    }
                }

                String departmentCode = null;
                if (StringUtils.hasText(req.getDepartmentCode())) {
                    departmentCode = req.getDepartmentCode().trim();
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

                String username = allocateUsername(conn, employeeNumber, extId);
                String passwordPlaceholder = "AD_PROVISIONED_" + UUID.randomUUID();

                long newId;
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO app_user (username, password_hash, role, department_code, name, is_system_admin) "
                                + "VALUES (?, ?, 'USER', ?, ?, false)",
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
                return new ProvisionUserResultResponse(newId, username);
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

    private static boolean mappingExists(Connection conn, String sourceSystem, String externalEmployeeId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM app_user_external_identity WHERE source_system = ? AND external_employee_id = ?")) {
            ps.setString(1, sourceSystem);
            ps.setString(2, externalEmployeeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String allocateUsername(Connection conn, String employeeNumber, String externalEmployeeId) throws Exception {
        String base = "emp";
        if (StringUtils.hasText(employeeNumber)) {
            base = "emp_" + employeeNumber.replaceAll("[^a-zA-Z0-9_]", "_");
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
