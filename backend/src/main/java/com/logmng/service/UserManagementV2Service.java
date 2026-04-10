package com.logmng.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ActivityActionType;
import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.dto.request.UserManagementV2DirectUserCreateRequest;
import com.logmng.exception.CustomException;
import com.logmng.util.ChangeReasonValidator;
import com.logmng.util.LocalUserInitialPassword;
import com.logmng.util.UserManagementReadScopeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Service
public class UserManagementV2Service {

    private static final int MAX_DEPARTMENT_CODE_LENGTH = 50;
    private static final int MAX_DEPARTMENT_NAME_LENGTH = 200;
    private static final int MAX_EMPLOYEE_NUMBER_LENGTH = 32;
    private static final int MAX_USER_NAME_LENGTH = 100;
    private static final int MAX_RANK_LENGTH = 50;
    private static final int MAX_RECENT = 10;
    private static final Pattern SAFE_CODE_PATTERN = Pattern.compile("[^A-Z0-9_\\-]");

    private final DataSource dataSource;
    private final DepartmentService departmentService;
    private final UserActivityLogService userActivityLogService;
    private final ObjectMapper activityAuditObjectMapper = new ObjectMapper();
    private final ConcurrentMap<String, QuickEntryHistory> quickEntryByActor = new ConcurrentHashMap<>();

    public UserManagementV2Service(DataSource dataSource,
                                   DepartmentService departmentService,
                                   @Autowired(required = false) UserActivityLogService userActivityLogService) {
        this.dataSource = dataSource;
        this.departmentService = departmentService;
        this.userActivityLogService = userActivityLogService;
    }

    public Map<String, Object> createRootDepartment(UserManagementV2CreateDepartmentRequest body,
                                                    String actorUsername,
                                                    String clientIp,
                                                    String userAgent,
                                                    String requestPath,
                                                    UserManagementReadScopeContext scopeCtx) {
        requireMutationInScope(scopeCtx, actorUsername, null, true);
        String reason = ChangeReasonValidator.requireValidChangeReason(body != null ? body.getChangeReason() : null);
        String name = requireTrimmed(body != null ? body.getName() : null, "name", MAX_DEPARTMENT_NAME_LENGTH);
        Integer sortOrder = body != null && body.getSortOrder() != null ? body.getSortOrder() : 0;
        String requestedCode = normalizeOptionalCode(body != null ? body.getCode() : null);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String code = allocateDepartmentCode(conn, requestedCode, name);
                insertDepartment(conn, code, null, name, sortOrder);
                conn.commit();
                emitDepartmentCreateAudit(actorUsername, ActivityActionType.DEPARTMENT_CREATE_ROOT,
                        requestPath != null ? requestPath : "/api/user-management-v2/departments/root",
                        reason, code, null, name, sortOrder, body, null, clientIp, userAgent);
                return toDepartmentResponse(code, null, name, sortOrder);
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("v2 루트 부서 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createChildDepartment(String parentDepartmentId,
                                                     UserManagementV2CreateDepartmentRequest body,
                                                     String actorUsername,
                                                     String clientIp,
                                                     String userAgent,
                                                     String requestPath,
                                                     UserManagementReadScopeContext scopeCtx) {
        String parentCode = requireTrimmed(parentDepartmentId, "parentDepartmentId", MAX_DEPARTMENT_CODE_LENGTH);
        ensureParentIsPersistedDepartment(parentCode, "하위 부서");
        String reason = ChangeReasonValidator.requireValidChangeReason(body != null ? body.getChangeReason() : null);
        String name = requireTrimmed(body != null ? body.getName() : null, "name", MAX_DEPARTMENT_NAME_LENGTH);
        Integer sortOrder = body != null && body.getSortOrder() != null ? body.getSortOrder() : 0;
        String requestedCode = normalizeOptionalCode(body != null ? body.getCode() : null);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String resolvedParent = requirePersistedDepartmentCode(conn, parentCode);
                requireMutationInScope(scopeCtx, actorUsername, resolvedParent, false);
                String code = allocateDepartmentCode(conn, requestedCode, name);
                insertDepartment(conn, code, resolvedParent, name, sortOrder);
                conn.commit();
                String path = requestPath != null && !requestPath.isBlank()
                        ? requestPath
                        : ("/api/user-management-v2/departments/" + resolvedParent + "/children");
                emitDepartmentCreateAudit(actorUsername, ActivityActionType.DEPARTMENT_CREATE_CHILD,
                        path, reason, code, resolvedParent, name, sortOrder, body, parentCode, clientIp, userAgent);
                return toDepartmentResponse(code, resolvedParent, name, sortOrder);
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("v2 하위 부서 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createDirectUser(UserManagementV2DirectUserCreateRequest body,
                                                String actorUsername,
                                                String clientIp,
                                                String userAgent,
                                                String requestPath,
                                                UserManagementReadScopeContext scopeCtx) {
        String departmentCode = requireTrimmed(body != null ? body.getDepartmentId() : null,
                "departmentId", MAX_DEPARTMENT_CODE_LENGTH);
        ensureParentIsPersistedDepartment(departmentCode, "사용자 등록");
        String reason = ChangeReasonValidator.requireValidChangeReason(body != null ? body.getChangeReason() : null);
        String employeeNumber = requireTrimmed(body != null ? body.getEmployeeNumber() : null,
                "employeeNumber", MAX_EMPLOYEE_NUMBER_LENGTH);
        String name = requireTrimmed(body != null ? body.getName() : null, "name", MAX_USER_NAME_LENGTH);
        String rank = requireTrimmed(body != null ? body.getRank() : null, "rank", MAX_RANK_LENGTH);
        Long permissionGroupId = body != null ? body.getPermissionGroupId() : null;
        if (permissionGroupId == null) {
            throw CustomException.badRequest("permissionGroupId는 필수입니다.", "INVALID_INPUT");
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String resolvedDept = requirePersistedDepartmentCode(conn, departmentCode);
                requireMutationInScope(scopeCtx, actorUsername, resolvedDept, false);
                ensurePermissionGroupExists(conn, permissionGroupId);
                ensureEmployeeNumberAvailable(conn, employeeNumber);

                String username = allocateUsername(conn, employeeNumber);
                String initialPasswordStored = LocalUserInitialPassword.storedValueForNewLocalUser();

                long newUserId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO app_user (username, employee_number, password_hash, role, department_code, name, rank, is_system_admin) "
                                + "VALUES (?, ?, ?, 'USER', ?, ?, ?, false)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, employeeNumber);
                    ps.setString(3, initialPasswordStored);
                    ps.setString(4, resolvedDept);
                    ps.setString(5, name);
                    ps.setString(6, rank);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new IllegalStateException("no generated key for app_user");
                        }
                        newUserId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES (?, ?)")) {
                    ps.setString(1, username);
                    ps.setLong(2, permissionGroupId);
                    ps.executeUpdate();
                }

                conn.commit();
                updateQuickEntry(actorUsername, employeeNumber, name, rank, permissionGroupId);
                String path = requestPath != null ? requestPath : "/api/user-management-v2/users/direct";
                emitUserCreateAudit(actorUsername, reason, newUserId, employeeNumber, resolvedDept, permissionGroupId,
                        name, rank, body, path, clientIp, userAgent);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", newUserId);
                data.put("employeeNumber", employeeNumber);
                data.put("name", name);
                data.put("rank", rank);
                data.put("departmentId", resolvedDept);
                data.put("permissionGroupId", permissionGroupId);
                data.put("createdAt", OffsetDateTime.now().toString());
                return data;
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("v2 직접 사용자 등록 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Hard-deletes a {@code department} row after safety checks (children, active users, org-link mapping).
     * {@code departmentIdRaw} is the path identifier (typically {@code department.code}); resolved case-insensitively like other v2 APIs.
     */
    public Map<String, Object> deleteDepartment(String departmentIdRaw,
                                                UserDeleteRequest body,
                                                String actorUsername,
                                                String clientIp,
                                                String userAgent,
                                                String requestPath,
                                                UserManagementReadScopeContext scopeCtx) {
        String idTrim = requireTrimmed(departmentIdRaw, "departmentId", MAX_DEPARTMENT_CODE_LENGTH);
        ensureParentIsPersistedDepartment(idTrim, "부서 삭제");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String code = requirePersistedDepartmentCode(conn, idTrim);
                requireMutationInScope(scopeCtx, actorUsername, code, false);
                String reason = ChangeReasonValidator.requireValidChangeReason(body != null ? body.getChangeReason() : null);
                Map<String, Object> beforeSnap = loadDepartmentSnapshot(conn, code);
                if (beforeSnap == null) {
                    throw CustomException.notFound("부서를 찾을 수 없습니다.", "DEPARTMENT_NOT_FOUND");
                }
                if (hasChildDepartments(conn, code)) {
                    throw CustomException.conflict(
                            "하위 부서가 있어 삭제할 수 없습니다. 먼저 하위 부서를 정리하세요.",
                            "DEPARTMENT_HAS_CHILDREN");
                }
                if (countActiveUsersInDepartment(conn, code) > 0) {
                    throw CustomException.conflict(
                            "해당 부서에 활성 사용자가 있어 삭제할 수 없습니다.",
                            "DEPARTMENT_HAS_ACTIVE_USERS");
                }
                if (hasDepartmentOrgLink(conn, code)) {
                    throw CustomException.conflict(
                            "외부 조직 매핑이 있어 삭제할 수 없습니다.",
                            "DEPARTMENT_ORG_LINK_REFERENCES");
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM department WHERE code = ?")) {
                    ps.setString(1, code);
                    int n = ps.executeUpdate();
                    if (n != 1) {
                        throw CustomException.notFound("부서를 찾을 수 없습니다.", "DEPARTMENT_NOT_FOUND");
                    }
                }
                conn.commit();
                String path = requestPath != null && !requestPath.isBlank()
                        ? requestPath
                        : ("/api/user-management-v2/departments/" + code);
                emitDepartmentDeleteAudit(actorUsername, reason, code, beforeSnap, path, clientIp, userAgent);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("departmentId", code);
                return data;
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("v2 부서 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Out-of-scope mutations for UM v2 read scope (req 20260409): 403 FUNCTION_NOT_ALLOWED.
     */
    private void requireMutationInScope(UserManagementReadScopeContext ctx, String actorUsername,
                                        String targetDepartmentCode, boolean creatingRoot) {
        if (ctx == null || !ctx.appliesUmV2Screen() || !ctx.isNarrowRead()) {
            return;
        }
        if (creatingRoot) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        String scope = ctx.getEffectiveScope();
        String actorDept = loadDepartmentCodeForUsername(actorUsername);
        if (targetDepartmentCode == null || targetDepartmentCode.isBlank()) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        String t = targetDepartmentCode.trim();
        if ("self".equalsIgnoreCase(scope)) {
            if (actorDept == null || !actorDept.trim().equalsIgnoreCase(t)) {
                throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
            }
            return;
        }
        if ("team".equalsIgnoreCase(scope)) {
            if (actorDept == null || !departmentService.isSameOrDescendantDepartment(actorDept.trim(), t)) {
                throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
            }
        }
    }

    private String loadDepartmentCodeForUsername(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return null;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT department_code FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, actorUsername.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("department_code");
                    }
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public Map<String, Object> getQuickEntryOptions(String actorUsername, List<String> fields, Integer limit,
                                                    UserManagementReadScopeContext scopeCtx) {
        if (scopeCtx != null && scopeCtx.isNarrowRead()) {
            // History is keyed by actor only; narrow UM v2 read scope does not merge other principals' values.
        }
        int effectiveLimit = (limit == null) ? MAX_RECENT : limit;
        if (effectiveLimit <= 0 || effectiveLimit > 20) {
            throw CustomException.badRequest("limit은 1 이상 20 이하여야 합니다.", "INVALID_INPUT");
        }
        List<String> requested = normalizeRequestedFields(fields);
        QuickEntryHistory history = quickEntryByActor.get(actorUsername != null ? actorUsername.trim() : "");

        Map<String, Object> data = new LinkedHashMap<>();
        if (requested.contains("employeeNumber")) {
            data.put("employeeNumber", fieldOptions(
                    history != null ? history.previousEmployeeNumber : null,
                    history != null ? history.employeeNumbers : null,
                    effectiveLimit));
        }
        if (requested.contains("name")) {
            data.put("name", fieldOptions(
                    history != null ? history.previousName : null,
                    history != null ? history.names : null,
                    effectiveLimit));
        }
        if (requested.contains("rank")) {
            data.put("rank", fieldOptions(
                    history != null ? history.previousRank : null,
                    history != null ? history.ranks : null,
                    effectiveLimit));
        }
        if (requested.contains("permissionGroupId")) {
            data.put("permissionGroupId", fieldOptions(
                    history != null ? history.previousPermissionGroupId : null,
                    history != null ? history.permissionGroupIds : null,
                    effectiveLimit));
        }
        return data;
    }

    private static Map<String, Object> toDepartmentResponse(String code, String parentCode, String name, Integer sortOrder) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departmentId", code);
        data.put("name", name);
        data.put("code", code);
        data.put("parentDepartmentId", parentCode);
        data.put("sortOrder", sortOrder);
        return data;
    }

    private void insertDepartment(Connection conn, String code, String parentCode, String name, Integer sortOrder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO department (code, parent_code, name, sort_order) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, code);
            if (parentCode == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, parentCode);
            }
            ps.setString(3, name);
            ps.setInt(4, sortOrder != null ? sortOrder : 0);
            ps.executeUpdate();
        }
    }

    private static String requireTrimmed(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw CustomException.badRequest(fieldName + "은(는) 필수이며 공백일 수 없습니다.", "INVALID_INPUT");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw CustomException.badRequest(fieldName + "은(는) " + maxLength + "자 이하여야 합니다.", "INVALID_INPUT");
        }
        return trimmed;
    }

    private static String normalizeOptionalCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_DEPARTMENT_CODE_LENGTH) {
            throw CustomException.badRequest("code는 " + MAX_DEPARTMENT_CODE_LENGTH + "자 이하여야 합니다.", "INVALID_INPUT");
        }
        return trimmed;
    }

    private static String codeFromName(String name) {
        String upper = SAFE_CODE_PATTERN.matcher(name.toUpperCase(Locale.ROOT)).replaceAll("_");
        upper = upper.replaceAll("_+", "_");
        if (upper.startsWith("_")) upper = upper.substring(1);
        if (upper.endsWith("_")) upper = upper.substring(0, upper.length() - 1);
        if (upper.isEmpty()) {
            upper = "DEPT";
        }
        if (upper.length() > 40) {
            upper = upper.substring(0, 40);
        }
        return upper;
    }

    private static String allocateDepartmentCode(Connection conn, String requestedCode, String name) throws SQLException {
        if (requestedCode != null) {
            if (departmentExists(conn, requestedCode)) {
                throw CustomException.conflict("이미 존재하는 부서 코드입니다.", "DEPARTMENT_CODE_DUPLICATED");
            }
            return requestedCode;
        }
        String base = codeFromName(name);
        int i = 0;
        while (i < 1000) {
            String candidate = (i == 0) ? base : base + "_" + i;
            if (candidate.length() > MAX_DEPARTMENT_CODE_LENGTH) {
                candidate = candidate.substring(0, MAX_DEPARTMENT_CODE_LENGTH);
            }
            if (!departmentExists(conn, candidate)) {
                return candidate;
            }
            i++;
        }
        return "DEPT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static boolean departmentExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM department WHERE code = ? LIMIT 1")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * User-permission hierarchy appends a synthetic "미배치" bucket ({@link UserPermissionHierarchyService#UNASSIGNED_DEPARTMENT_CODE})
     * that is not a row in {@code department}. Treat using it as a parent/target as a client error so operators do not see 404.
     */
    private static void ensureParentIsPersistedDepartment(String departmentCode, String contextLabel) {
        if (UserPermissionHierarchyService.UNASSIGNED_DEPARTMENT_CODE.equals(departmentCode)) {
            throw CustomException.badRequest(
                    "미배치 노드는 부서 마스터에 없습니다. 실제 부서를 선택한 뒤 다시 시도하세요. (" + contextLabel + ")",
                    "INVALID_INPUT");
        }
    }

    /**
     * Returns canonical {@code department.code} as stored in DB. Tries exact match, then
     * case-insensitive match on {@code trim(code)} (PostgreSQL / H2) so path/hierarchy
     * identifiers still resolve if casing differs.
     */
    private static String resolvePersistedDepartmentCode(Connection conn, String requestedCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT code FROM department WHERE code = ? LIMIT 1")) {
            ps.setString(1, requestedCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("code");
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT code FROM department WHERE lower(trim(code)) = lower(trim(?)) LIMIT 1")) {
            ps.setString(1, requestedCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("code");
                }
            }
        }
        return null;
    }

    private static String requirePersistedDepartmentCode(Connection conn, String requestedCode) throws SQLException {
        String resolved = resolvePersistedDepartmentCode(conn, requestedCode);
        if (resolved == null) {
            throw CustomException.notFound("부서를 찾을 수 없습니다.", "DEPARTMENT_NOT_FOUND");
        }
        return resolved;
    }

    private static boolean hasChildDepartments(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM department WHERE parent_code = ? LIMIT 1")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int countActiveUsersInDepartment(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM app_user WHERE department_code = ? AND deleted_at IS NULL")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private static boolean hasDepartmentOrgLink(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM department_org_link WHERE department_code = ? LIMIT 1")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Map<String, Object> loadDepartmentSnapshot(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, parent_code, sort_order FROM department WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> before = new LinkedHashMap<>();
                before.put("name", rs.getString("name"));
                before.put("parentDepartmentCode", rs.getString("parent_code"));
                int sort = rs.getInt("sort_order");
                before.put("sortOrder", rs.wasNull() ? 0 : sort);
                return before;
            }
        }
    }

    private static void ensurePermissionGroupExists(Connection conn, Long permissionGroupId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM permission_group WHERE id = ? LIMIT 1")) {
            ps.setLong(1, permissionGroupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw CustomException.notFound("권한 그룹을 찾을 수 없습니다.", "PERMISSION_GROUP_NOT_FOUND");
                }
            }
        }
    }

    private static void ensureEmployeeNumberAvailable(Connection conn, String employeeNumber) throws SQLException {
        AppUserEmployeeNumberUniqueness.ensureAvailableForActiveUser(conn, employeeNumber);
    }

    private static String allocateUsername(Connection conn, String employeeNumber) throws SQLException {
        String base = ("umv2_" + employeeNumber).replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (base.length() > 90) {
            base = base.substring(0, 90);
        }
        int i = 0;
        while (i < 1000) {
            String candidate = (i == 0) ? base : base + "_" + i;
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM app_user WHERE username = ? LIMIT 1")) {
                ps.setString(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            }
            i++;
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static List<String> normalizeRequestedFields(List<String> fields) {
        List<String> defaults = List.of("employeeNumber", "name", "rank", "permissionGroupId");
        if (fields == null || fields.isEmpty()) {
            return defaults;
        }
        List<String> out = new ArrayList<>();
        for (String f : fields) {
            if (f == null || f.trim().isEmpty()) {
                continue;
            }
            String trimmed = f.trim();
            if (!defaults.contains(trimmed)) {
                throw CustomException.badRequest("지원하지 않는 fields 값입니다: " + trimmed, "INVALID_INPUT");
            }
            if (!out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        if (out.isEmpty()) {
            throw CustomException.badRequest("fields는 최소 1개 이상이어야 합니다.", "INVALID_INPUT");
        }
        return out;
    }

    private static <T> Map<String, Object> fieldOptions(T previous, ArrayDeque<T> queue, int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("previous", previous);
        List<T> recent = new ArrayList<>();
        if (queue != null) {
            int count = 0;
            for (T v : queue) {
                recent.add(v);
                count++;
                if (count >= limit) {
                    break;
                }
            }
        }
        data.put("recent", recent);
        return data;
    }

    private void updateQuickEntry(String actorUsername, String employeeNumber, String name, String rank, Long permissionGroupId) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        QuickEntryHistory history = quickEntryByActor.computeIfAbsent(actorUsername.trim(), k -> new QuickEntryHistory());
        synchronized (history) {
            history.previousEmployeeNumber = employeeNumber;
            history.previousName = name;
            history.previousRank = rank;
            history.previousPermissionGroupId = permissionGroupId;
            addRecent(history.employeeNumbers, employeeNumber);
            addRecent(history.names, name);
            addRecent(history.ranks, rank);
            addRecent(history.permissionGroupIds, permissionGroupId);
        }
    }

    private static <T> void addRecent(ArrayDeque<T> queue, T value) {
        queue.remove(value);
        queue.addFirst(value);
        while (queue.size() > MAX_RECENT) {
            queue.removeLast();
        }
    }

    /**
     * Structured {@code departmentAdminV1} for v2 department creates; request_params holds allowlisted body fields only.
     */
    private void emitDepartmentCreateAudit(String actorUsername, ActivityActionType actionType, String requestPath,
                                           String changeReason, String departmentCode, String parentDepartmentCode,
                                           String departmentName, Integer sortOrder,
                                           UserManagementV2CreateDepartmentRequest body,
                                           String pathStyleParentRawOrNull,
                                           String clientIp, String userAgent) {
        if (userActivityLogService == null || actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        Map<String, Object> deptAdminV1 = new LinkedHashMap<>();
        deptAdminV1.put("schemaVersion", 1);
        deptAdminV1.put("operation", actionType == ActivityActionType.DEPARTMENT_CREATE_ROOT ? "CREATE_ROOT" : "CREATE_CHILD");
        deptAdminV1.put("changeReason", changeReason);
        deptAdminV1.put("departmentCode", departmentCode);
        if (parentDepartmentCode != null) {
            deptAdminV1.put("parentDepartmentCode", parentDepartmentCode);
        }
        if (departmentName != null) {
            deptAdminV1.put("name", departmentName);
        }
        deptAdminV1.put("sortOrder", sortOrder != null ? sortOrder : 0);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("departmentAdminV1", deptAdminV1);

        Map<String, Object> bodySnapshot = sanitizedDepartmentRequestBody(body, pathStyleParentRawOrNull);
        String requestParamsJson = buildSanitizedRequestParamsJson("POST", requestPath, bodySnapshot);

        userActivityLogService.saveActivityLog(
                actorUsername.trim(),
                actorUsername.trim(),
                actionType.getCode(),
                detail,
                clientIp,
                userAgent,
                "POST",
                requestPath,
                requestParamsJson,
                201,
                null,
                true,
                null
        );
    }

    private void emitDepartmentDeleteAudit(String actorUsername, String changeReason, String departmentCode,
                                           Map<String, Object> beforeSnapshot,
                                           String requestPath, String clientIp, String userAgent) {
        if (userActivityLogService == null || actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        Map<String, Object> deptAdminV1 = new LinkedHashMap<>();
        deptAdminV1.put("schemaVersion", 1);
        deptAdminV1.put("operation", "DELETE");
        deptAdminV1.put("changeReason", changeReason);
        deptAdminV1.put("departmentCode", departmentCode);
        if (beforeSnapshot != null && !beforeSnapshot.isEmpty()) {
            deptAdminV1.put("before", beforeSnapshot);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("departmentAdminV1", deptAdminV1);

        Map<String, Object> bodySnapshot = new LinkedHashMap<>();
        putIfNonBlank(bodySnapshot, "changeReason", changeReason);
        stripSensitiveKeysFromMap(bodySnapshot);
        String requestParamsJson = buildSanitizedRequestParamsJson("DELETE", requestPath, bodySnapshot);

        userActivityLogService.saveActivityLog(
                actorUsername.trim(),
                actorUsername.trim(),
                ActivityActionType.DEPARTMENT_DELETE.getCode(),
                detail,
                clientIp,
                userAgent,
                "DELETE",
                requestPath,
                requestParamsJson,
                200,
                null,
                true,
                null
        );
    }

    private void emitUserCreateAudit(String actorUsername, String reason, long userId, String employeeNumber,
                                     String departmentCode, Long permissionGroupId,
                                     String displayName, String rankLabel,
                                     UserManagementV2DirectUserCreateRequest body,
                                     String requestPath,
                                     String clientIp, String userAgent) {
        if (userActivityLogService == null || actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changeReason", reason);
        detail.put("targetUserId", userId);
        detail.put("employeeNumber", employeeNumber);
        detail.put("departmentCode", departmentCode);
        detail.put("permissionGroupId", permissionGroupId);
        detail.put("registrationSource", "USER_MANAGEMENT_V2_DIRECT");
        if (displayName != null) {
            detail.put("name", displayName);
        }
        if (rankLabel != null) {
            detail.put("rank", rankLabel);
        }

        String requestParamsJson = buildSanitizedRequestParamsJson("POST", requestPath, sanitizedDirectUserBody(body));

        userActivityLogService.saveActivityLog(
                actorUsername.trim(),
                actorUsername.trim(),
                ActivityActionType.USER_CREATE.getCode(),
                detail,
                clientIp,
                userAgent,
                "POST",
                requestPath,
                requestParamsJson,
                201,
                null,
                true,
                null
        );
    }

    private String buildSanitizedRequestParamsJson(String method, String path, Map<String, Object> bodyAllowlisted) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("method", method);
            root.put("path", path);
            root.put("body", bodyAllowlisted);
            return activityAuditObjectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> sanitizedDepartmentRequestBody(UserManagementV2CreateDepartmentRequest body,
                                                                      String pathParentCodeOrNull) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (body != null) {
            putIfNonBlank(m, "changeReason", body.getChangeReason());
            putIfNonBlank(m, "name", body.getName());
            putIfNonBlank(m, "code", body.getCode());
            if (body.getSortOrder() != null) {
                m.put("sortOrder", body.getSortOrder());
            }
            putIfNonBlank(m, "parentDepartmentId", body.getParentDepartmentId());
        }
        if (pathParentCodeOrNull != null && !pathParentCodeOrNull.isBlank()) {
            m.putIfAbsent("parentDepartmentId", pathParentCodeOrNull.trim());
        }
        stripSensitiveKeysFromMap(m);
        return m;
    }

    private static Map<String, Object> sanitizedDirectUserBody(UserManagementV2DirectUserCreateRequest body) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (body == null) {
            return m;
        }
        putIfNonBlank(m, "changeReason", body.getChangeReason());
        putIfNonBlank(m, "departmentId", body.getDepartmentId());
        putIfNonBlank(m, "employeeNumber", body.getEmployeeNumber());
        putIfNonBlank(m, "name", body.getName());
        putIfNonBlank(m, "rank", body.getRank());
        if (body.getPermissionGroupId() != null) {
            m.put("permissionGroupId", body.getPermissionGroupId());
        }
        stripSensitiveKeysFromMap(m);
        return m;
    }

    private static void putIfNonBlank(Map<String, Object> m, String key, String value) {
        if (key == null || value == null) {
            return;
        }
        String t = value.trim();
        if (!t.isEmpty()) {
            m.put(key, t);
        }
    }

    private static void stripSensitiveKeysFromMap(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return;
        }
        m.keySet().removeIf(k -> k != null && isSensitiveAuditKey(k));
    }

    private static boolean isSensitiveAuditKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("pwd")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("authorization");
    }

    private static final class QuickEntryHistory {
        private String previousEmployeeNumber;
        private String previousName;
        private String previousRank;
        private Long previousPermissionGroupId;
        private final ArrayDeque<String> employeeNumbers = new ArrayDeque<>();
        private final ArrayDeque<String> names = new ArrayDeque<>();
        private final ArrayDeque<String> ranks = new ArrayDeque<>();
        private final ArrayDeque<Long> permissionGroupIds = new ArrayDeque<>();
    }
}
