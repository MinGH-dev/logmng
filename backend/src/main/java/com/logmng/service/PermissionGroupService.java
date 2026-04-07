package com.logmng.service;

import com.logmng.activity.PermissionGroupAuditContext;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.diagnostic.PermissionGroupScreenDiagnosticLog;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permission group CRUD and user-group assignment. §14. Admin-only APIs.
 */
@Service
public class PermissionGroupService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGroupService.class);
    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_USER_ID_LENGTH = 100;
    private static final int MAX_CHANGE_REASON_LENGTH = 2000;

    private final DataSource dataSource;
    private final AppUserResolver appUserResolver;

    @Value("${app.diagnostic.permission-group-screen:false}")
    private boolean diagnosticPermissionGroupScreen;

    public PermissionGroupService(DataSource dataSource, AppUserResolver appUserResolver) {
        this.dataSource = dataSource;
        this.appUserResolver = appUserResolver;
    }

    public List<PermissionGroupResponse> listAll() {
        PermissionGroupScreenDiagnosticLog.debug(diagnosticPermissionGroupScreen, "listAll_enter", "");
        List<PermissionGroupResponse> list = new ArrayList<>();
        Long sqlContextGroupId = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, code, name, description, sort_order FROM permission_group ORDER BY sort_order, code";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sqlContextGroupId = null;
                    PermissionGroupResponse r = mapRowToResponse(rs);
                    sqlContextGroupId = r.getId();
                    r.setAllowedScreens(loadAllowedScreens(conn, sqlContextGroupId));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            PermissionGroupScreenDiagnosticLog.sqlException(diagnosticPermissionGroupScreen, "listAll", sqlContextGroupId, e);
            log.error("Permission group list failed", e);
            throw new RuntimeException("권한 그룹 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    public PermissionGroupResponse findById(Long id) {
        if (id == null) {
            throw CustomException.notFound("권한 그룹을 찾을 수 없습니다.", "PERMISSION_GROUP_NOT_FOUND");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, code, name, description, sort_order FROM permission_group WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PermissionGroupResponse r = mapRowToResponse(rs);
                        r.setAllowedScreens(loadAllowedScreens(conn, r.getId()));
                        return r;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Permission group find failed: id={}", id, e);
            throw new RuntimeException("권한 그룹 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw CustomException.notFound("권한 그룹을 찾을 수 없습니다: id=" + id, "PERMISSION_GROUP_NOT_FOUND");
    }

    public PermissionGroupResponse create(PermissionGroupCreateRequest req) {
        validateCode(req.getCode());
        validateName(req.getName());
        validateAllowedScreens(req.getAllowedScreens());
        String code = trim(req.getCode());
        String name = trim(req.getName());
        if (code.isEmpty() || name.isEmpty()) {
            throw CustomException.badRequest("code와 name은 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        if (existsByCode(code)) {
            throw CustomException.badRequest("이미 존재하는 권한 그룹 코드입니다: " + code, "INVALID_INPUT");
        }
        String description = trim(req.getDescription());
        Integer sortOrder = req.getSortOrder() != null ? req.getSortOrder() : 0;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO permission_group (code, name, description, sort_order) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                ps.setString(2, name);
                ps.setString(3, description.isEmpty() ? null : description);
                ps.setInt(4, sortOrder);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        saveAllowedScreens(conn, id, req.getAllowedScreens());
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Permission group create failed: code={}", code, e);
            throw new RuntimeException("권한 그룹 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw new RuntimeException("권한 그룹 생성 후 ID를 읽지 못했습니다.");
    }

    public PermissionGroupResponse update(Long id, PermissionGroupUpdateRequest req) {
        if (req.getChangeReason() != null && req.getChangeReason().length() > MAX_CHANGE_REASON_LENGTH) {
            throw CustomException.badRequest(
                    "changeReason은 " + MAX_CHANGE_REASON_LENGTH + "자 이하여야 합니다.", "INVALID_INPUT");
        }
        if (req.getAllowedScreens() != null) {
            validateAllowedScreens(req.getAllowedScreens());
        }
        PermissionGroupResponse existing = findById(id);
        PermissionGroupAuditContext.setBeforeState(existing);
        String code = req.getCode() != null ? trim(req.getCode()) : existing.getCode();
        String name = req.getName() != null ? trim(req.getName()) : existing.getName();
        if (code.isEmpty()) {
            throw CustomException.badRequest("code는 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        if (name.isEmpty()) {
            throw CustomException.badRequest("name은 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        if (existsByCodeExcludingId(code, id)) {
            throw CustomException.badRequest("이미 존재하는 권한 그룹 코드입니다: " + code, "INVALID_INPUT");
        }
        String description = req.getDescription() != null ? trim(req.getDescription()) : existing.getDescription();
        Integer sortOrder = req.getSortOrder() != null ? req.getSortOrder() : existing.getSortOrder();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE permission_group SET code = ?, name = ?, description = ?, sort_order = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setString(2, name);
                ps.setString(3, (description == null || description.isEmpty()) ? null : description);
                ps.setInt(4, sortOrder);
                ps.setLong(5, id);
                if (ps.executeUpdate() == 0) {
                    throw CustomException.notFound("권한 그룹을 찾을 수 없습니다: id=" + id, "PERMISSION_GROUP_NOT_FOUND");
                }
            }
            if (req.getAllowedScreens() != null) {
                saveAllowedScreens(conn, id, req.getAllowedScreens());
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Permission group update failed: id={}", id, e);
            throw new RuntimeException("권한 그룹 수정 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return findById(id);
    }

    public void delete(Long id) {
        PermissionGroupResponse existing = findById(id);
        PermissionGroupAuditContext.setBeforeState(existing);
        int userCount = countUsersInGroup(id);
        if (userCount > 0) {
            throw CustomException.badRequest("사용자가 배정된 권한 그룹은 삭제할 수 없습니다. 먼저 사용자 배정을 해제하세요.", "PERMISSION_GROUP_HAS_USERS");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM permission_group WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Permission group delete failed: id={}", id, e);
            throw new RuntimeException("권한 그룹 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public AssignUserToGroupResponse assignUser(Long groupId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        String uid = userId.trim();
        if (uid.length() > MAX_USER_ID_LENGTH) {
            throw CustomException.badRequest("유효하지 않은 userId입니다.", "INVALID_INPUT");
        }
        PermissionGroupAuditContext.clearAssignAudit();
        PermissionGroupResponse group = findById(groupId);
        ensureUserExists(uid);
        if (isUserInGroup(groupId, uid)) {
            throw CustomException.badRequest("해당 사용자는 이미 이 권한 그룹에 배정되어 있습니다.", "USER_ALREADY_IN_GROUP");
        }
        Long previousGroupId = findCurrentPermissionGroupIdForUser(uid);
        if (previousGroupId != null) {
            PermissionGroupAuditContext.setAssignPreviousState(findById(previousGroupId));
        } else {
            PermissionGroupAuditContext.setAssignPreviousState(null);
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delPs = conn.prepareStatement(
                        "DELETE FROM app_user_permission_group WHERE user_id = ?")) {
                    delPs.setString(1, uid);
                    delPs.executeUpdate();
                }
                try (PreparedStatement insPs = conn.prepareStatement(
                        "INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES (?, ?)")) {
                    insPs.setString(1, uid);
                    insPs.setLong(2, groupId);
                    insPs.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            log.error("Assign user to group failed: groupId={}, userId={}", groupId, uid, e);
            throw new RuntimeException("사용자 배정 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        PermissionGroupAuditContext.setAssignAfterState(group);
        Long numericUserId = appUserResolver.getIdByUsername(uid);
        return new AssignUserToGroupResponse(numericUserId, groupId, group.getCode());
    }

    public void unassignUser(Long groupId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        PermissionGroupAuditContext.clearUnassignAudit();
        PermissionGroupResponse group = findById(groupId);
        PermissionGroupAuditContext.setUnassignGroupCode(group.getCode());
        PermissionGroupAuditContext.setUnassignBeforeState(group);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM app_user_permission_group WHERE permission_group_id = ? AND user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, groupId);
                ps.setString(2, userId.trim());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Unassign user from group failed: groupId={}, userId={}", groupId, userId, e);
            throw new RuntimeException("사용자 배정 해제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Returns union of allowed screen IDs from all permission groups the user belongs to.
     * Excludes screens with read=false (explicit). read=null or read=true grants access.
     * Used for login/me response. Caller should pass all screens for ADMIN.
     */
    public List<String> getAllowedScreenIdsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        Set<String> screens = new LinkedHashSet<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT DISTINCT pgs.screen_id FROM permission_group_screen pgs " +
                    "INNER JOIN app_user_permission_group aupg ON pgs.permission_group_id = aupg.permission_group_id " +
                    "WHERE aupg.user_id = ? AND (pgs.read IS NULL OR pgs.read = true) ORDER BY pgs.screen_id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        screens.add(rs.getString("screen_id"));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Get allowed screens for user failed: userId={}", userId, e);
            return List.of();
        }
        return new ArrayList<>(screens);
    }

    /**
     * Returns per-screen read/write/approve/decrypt from permission_group_screen for the user's groups.
     * Key = screen_id, value = {read, write, approve, decrypt} (Boolean; null = use derivation).
     * When user has multiple groups with same screen, aggregates: write/approve/decrypt=true if any has true or null. decrypt only for main (req 20260306).
     */
    public Map<String, ScreenFunctionFromDb> getScreenFunctionsForUser(String userId) {
        Map<String, ScreenFunctionFromDb> result = new HashMap<>();
        if (userId == null || userId.isBlank()) {
            return result;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT pgs.screen_id, pgs.read, pgs.write, pgs.approve, pgs.decrypt FROM permission_group_screen pgs " +
                    "INNER JOIN app_user_permission_group aupg ON pgs.permission_group_id = aupg.permission_group_id " +
                    "WHERE aupg.user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String screenId = rs.getString("screen_id");
                        if (screenId == null || screenId.isBlank()) continue;
                        Boolean readVal = rs.getObject("read", Boolean.class);
                        Boolean writeVal = rs.getObject("write", Boolean.class);
                        Boolean approveVal = rs.getObject("approve", Boolean.class);
                        Boolean decryptVal = rs.getObject("decrypt", Boolean.class);
                        result.merge(screenId, new ScreenFunctionFromDb(readVal, writeVal, approveVal, decryptVal), this::mergeScreenFunction);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Get screen functions for user failed: userId={}", userId, e);
            return result;
        }
        return result;
    }

    private ScreenFunctionFromDb mergeScreenFunction(ScreenFunctionFromDb a, ScreenFunctionFromDb b) {
        Boolean read = orNullAsTrue(a.read, b.read);
        Boolean write = mostPermissive(a.write, b.write);
        Boolean approve = mostPermissive(a.approve, b.approve);
        Boolean decrypt = mostPermissive(a.decrypt, b.decrypt);
        return new ScreenFunctionFromDb(read, write, approve, decrypt);
    }

    private static Boolean orNullAsTrue(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
        if (a == null || b == null) return null;
        return false;
    }

    /** For write/approve: true > null (derivation) > false. */
    private static Boolean mostPermissive(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
        if (a == null || b == null) return null;
        return false;
    }

    /** Per-screen read/write/approve/decrypt from DB. null = use derivation. decrypt only for main (req 20260306). */
    public static final class ScreenFunctionFromDb {
        public final Boolean read;
        public final Boolean write;
        public final Boolean approve;
        public final Boolean decrypt;

        public ScreenFunctionFromDb(Boolean read, Boolean write, Boolean approve, Boolean decrypt) {
            this.read = read;
            this.write = write;
            this.approve = approve;
            this.decrypt = decrypt;
        }
    }

    /**
     * Returns per-screen scope for activity-log, statistics, search-history.
     * Key = screen_id, value = 'self', 'team', or 'all'. When user has multiple groups, if any has 'all', use 'all'; else first wins.
     * NULL or missing scope in DB = 'team' (default for scope-supporting screens). Per req 20250304-team-scope-default-and-approval.
     */
    public Map<String, String> getScreenScopesForUser(String userId) {
        Map<String, String> scopes = new HashMap<>();
        if (userId == null || userId.isBlank()) {
            return scopes;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT pgs.screen_id, pgs.scope FROM permission_group_screen pgs " +
                    "INNER JOIN app_user_permission_group aupg ON pgs.permission_group_id = aupg.permission_group_id " +
                    "WHERE aupg.user_id = ? AND pgs.screen_id IN ('activity-log', 'statistics', 'search-history', 'pending-approvals')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String screenId = rs.getString("screen_id");
                        String scope = rs.getString("scope");
                        if (screenId == null || screenId.isBlank()) continue;
                        if (!ScreenConstants.supportsScope(screenId)) continue;
                        String effective = "all".equalsIgnoreCase(scope) ? "all"
                                : "team".equalsIgnoreCase(scope) ? "team"
                                : (scope != null && !scope.isBlank() && "self".equalsIgnoreCase(scope)) ? "self" : "team";
                        if ("all".equals(effective) || !scopes.containsKey(screenId)) {
                            scopes.put(screenId, effective);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Get screen scopes for user failed: userId={}", userId, e);
            return scopes;
        }
        return scopes;
    }

    public List<UserListItemResponse> listUsersInGroup(Long groupId) {
        findById(groupId);
        List<UserListItemResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT u.id, u.username, u.role, u.department_code, u.position, u.rank, u.is_system_admin, u.employee_number FROM app_user u " +
                    "INNER JOIN app_user_permission_group a ON u.username = a.user_id "
                    + "WHERE a.permission_group_id = ? AND u.deleted_at IS NULL ORDER BY u.username";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Long id = rs.getObject("id", Long.class);
                        String username = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        String position = rs.getString("position");
                        String rank = rs.getString("rank");
                        boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        String employeeNumber = rs.getString("employee_number");
                        boolean isApprover = false; // not loaded here; hierarchy uses DecryptApproverService for approver
                        list.add(new UserListItemResponse(id, username, role, departmentCode, isApprover, position, rank, isSystemAdmin, employeeNumber));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("List users in group failed: groupId={}", groupId, e);
            throw new RuntimeException("권한 그룹 사용자 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    private boolean existsByCode(String code) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM permission_group WHERE code = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("Permission group existsByCode failed: code={}", code, e);
            return false;
        }
    }

    private boolean existsByCodeExcludingId(String code, Long excludeId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM permission_group WHERE code = ? AND id != ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setLong(2, excludeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("Permission group existsByCodeExcludingId failed: code={}", code, e);
            return false;
        }
    }

    private int countUsersInGroup(Long groupId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*) FROM app_user_permission_group WHERE permission_group_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Count users in group failed: groupId={}", groupId, e);
            return 0;
        }
        return 0;
    }

    /**
     * Current permission_group_id for the user, if any.
     * Matches {@code user_id} as {@link AppUserResolver}-style username and, when present, as numeric {@code app_user.id}
     * string (some rows may store either per legacy/manual data).
     */
    private Long findCurrentPermissionGroupIdForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String u = userId.trim();
        Long numericId = appUserResolver.getIdByUsername(u);
        try (Connection conn = dataSource.getConnection()) {
            String sql;
            if (numericId != null) {
                sql = "SELECT permission_group_id FROM app_user_permission_group WHERE user_id = ? OR user_id = ? "
                        + "ORDER BY permission_group_id LIMIT 1";
            } else {
                sql = "SELECT permission_group_id FROM app_user_permission_group WHERE user_id = ? "
                        + "ORDER BY permission_group_id LIMIT 1";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, u);
                if (numericId != null) {
                    ps.setString(2, String.valueOf(numericId));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("permission_group_id");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("findCurrentPermissionGroupIdForUser failed: userId={}", userId, e);
            return null;
        }
        return null;
    }

    private boolean isUserInGroup(Long groupId, String userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM app_user_permission_group WHERE permission_group_id = ? AND user_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, groupId);
                ps.setString(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("Is user in group check failed: groupId={}, userId={}", groupId, userId, e);
            return false;
        }
    }

    private void ensureUserExists(String userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 사용자를 찾을 수 없습니다: " + userId, "USER_NOT_FOUND");
                    }
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("User exists check failed: userId={}", userId, e);
            throw new RuntimeException("사용자 확인 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private static PermissionGroupResponse mapRowToResponse(ResultSet rs) throws SQLException {
        return new PermissionGroupResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("sort_order") != null ? rs.getInt("sort_order") : 0
        );
    }

    private static void validateCode(String code) {
        if (code != null && code.trim().length() > MAX_CODE_LENGTH) {
            throw CustomException.badRequest("권한 그룹 코드는 " + MAX_CODE_LENGTH + "자를 초과할 수 없습니다.", "INVALID_INPUT");
        }
    }

    private static void validateName(String name) {
        if (name != null && name.trim().length() > MAX_NAME_LENGTH) {
            throw CustomException.badRequest("권한 그룹 이름은 " + MAX_NAME_LENGTH + "자를 초과할 수 없습니다.", "INVALID_INPUT");
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }

    private static String normalizeScreenId(String screenId) {
        return ScreenConstants.normalizeScreenIdForPermissionGroup(screenId);
    }

    private void validateAllowedScreens(List<AllowedScreenItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (AllowedScreenItem item : items) {
            if (item == null || item.getScreenId() == null || item.getScreenId().isBlank()) continue;
            String screenId = normalizeScreenId(item.getScreenId());
            if (!ScreenConstants.isValid(screenId)) {
                throw CustomException.badRequest("유효하지 않은 화면 ID입니다: " + item.getScreenId().trim(), "INVALID_SCREEN_ID");
            }
            if (ScreenConstants.supportsScope(screenId)) {
                String scope = item.getScope();
                if (scope != null && !scope.isBlank() && !"self".equalsIgnoreCase(scope) && !"team".equalsIgnoreCase(scope) && !"all".equalsIgnoreCase(scope)) {
                    throw CustomException.badRequest("scope는 'self', 'team', 'all' 중 하나여야 합니다: " + scope, "INVALID_INPUT");
                }
            }
            validateScreenFunctions(screenId, item.getRead(), item.getWrite(), item.getApprove(), item.getDecrypt());
        }
    }

    /**
     * Validates read/write/approve/decrypt per screen per §1.1.1. main, pb-feplog, java-fw-imagelog: read + optional decrypt; write/approve not allowed (req 20260318).
     */
    private void validateScreenFunctions(String screenId, Boolean read, Boolean write, Boolean approve, Boolean decrypt) {
        if (ScreenConstants.MAIN.equals(screenId)
                || ScreenConstants.PB_FEPLOG.equals(screenId)
                || ScreenConstants.PB_FEP_LOG_SEARCH.equals(screenId)
                || ScreenConstants.JAVA_FW_IMAGELOG.equals(screenId)) {
            if (Boolean.TRUE.equals(write) || Boolean.TRUE.equals(approve)) {
                throw CustomException.badRequest("해당 화면은 조회 및 복호화만 지원합니다. write 또는 approve를 지정할 수 없습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
            }
        }
        if (Boolean.TRUE.equals(write) && !ScreenConstants.supportsWrite(screenId)) {
            throw CustomException.badRequest("해당 화면은 write를 지원하지 않습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
        }
        if (Boolean.TRUE.equals(approve) && !ScreenConstants.supportsApprove(screenId)) {
            throw CustomException.badRequest("해당 화면은 approve를 지원하지 않습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
        }
        if (Boolean.TRUE.equals(decrypt) && !ScreenConstants.supportsDecrypt(screenId)) {
            throw CustomException.badRequest("해당 화면은 decrypt(복호화)를 지원하지 않습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
        }
    }

    private List<AllowedScreenItem> loadAllowedScreens(Connection conn, long groupId) throws SQLException {
        PermissionGroupScreenDiagnosticLog.debug(diagnosticPermissionGroupScreen, "loadAllowedScreens_enter",
                "permissionGroupId=" + groupId);
        List<AllowedScreenItem> screens = new ArrayList<>();
        String sql = "SELECT screen_id, scope, read, write, approve, decrypt FROM permission_group_screen WHERE permission_group_id = ? ORDER BY screen_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String screenId = rs.getString("screen_id");
                    String scope = rs.getString("scope");
                    if (screenId != null && !screenId.isBlank()) {
                        AllowedScreenItem item = new AllowedScreenItem();
                        String normalized = normalizeScreenId(screenId);
                        item.setScreenId(normalized);
                        if (ScreenConstants.supportsScope(normalized)) {
                            String scopeVal = (scope == null || scope.isBlank()) ? "team"
                                    : "all".equalsIgnoreCase(scope) ? "all" : "team".equalsIgnoreCase(scope) ? "team" : "self";
                            item.setScope(scopeVal);
                        }
                        Boolean readVal = rs.getObject("read", Boolean.class);
                        Boolean writeVal = rs.getObject("write", Boolean.class);
                        Boolean approveVal = rs.getObject("approve", Boolean.class);
                        Boolean decryptVal = rs.getObject("decrypt", Boolean.class);
                        if (readVal != null) item.setRead(readVal);
                        if (writeVal != null) item.setWrite(writeVal);
                        if (approveVal != null) item.setApprove(approveVal);
                        if (decryptVal != null) item.setDecrypt(decryptVal);
                        screens.add(item);
                    }
                }
            }
        }
        return screens;
    }

    private void saveAllowedScreens(Connection conn, long groupId, List<AllowedScreenItem> items) throws SQLException {
        String deleteSql = "DELETE FROM permission_group_screen WHERE permission_group_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setLong(1, groupId);
            ps.executeUpdate();
        }
        if (items != null && !items.isEmpty()) {
            String insertSql = "INSERT INTO permission_group_screen (permission_group_id, screen_id, scope, read, write, approve, decrypt) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (AllowedScreenItem item : items) {
                    if (item == null || item.getScreenId() == null || item.getScreenId().isBlank()) continue;
                    String screenId = normalizeScreenId(item.getScreenId());
                    ps.setLong(1, groupId);
                    ps.setString(2, screenId);
                    String scope = null;
                    if (ScreenConstants.supportsScope(screenId)) {
                        String s = item.getScope();
                        scope = "all".equalsIgnoreCase(s) ? "all" : "team".equalsIgnoreCase(s) ? "team" : (s != null && !s.isBlank() && "self".equalsIgnoreCase(s) ? "self" : "team");
                        // Approval scope fixed to department: coerce to team when approve=true (req 20260306)
                        if (ScreenConstants.supportsApprove(screenId) && Boolean.TRUE.equals(item.getApprove())) {
                            scope = "team";
                        }
                    }
                    ps.setString(3, scope);
                    ps.setObject(4, item.getRead());
                    ps.setObject(5, item.getWrite());
                    ps.setObject(6, item.getApprove());
                    ps.setObject(7, item.getDecrypt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }
}
