package com.logmng.service;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final DataSource dataSource;

    public PermissionGroupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PermissionGroupResponse> listAll() {
        List<PermissionGroupResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, code, name, description, sort_order FROM permission_group ORDER BY sort_order, code";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PermissionGroupResponse r = mapRowToResponse(rs);
                    r.setAllowedScreens(loadAllowedScreens(conn, r.getId()));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
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
        if (req.getAllowedScreens() != null) {
            validateAllowedScreens(req.getAllowedScreens());
        }
        PermissionGroupResponse existing = findById(id);
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
        PermissionGroupResponse group = findById(groupId);
        ensureUserExists(uid);
        if (isUserInGroup(groupId, uid)) {
            throw CustomException.badRequest("해당 사용자는 이미 이 권한 그룹에 배정되어 있습니다.", "USER_ALREADY_IN_GROUP");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uid);
                ps.setLong(2, groupId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Assign user to group failed: groupId={}, userId={}", groupId, uid, e);
            throw new RuntimeException("사용자 배정 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return new AssignUserToGroupResponse(uid, groupId, group.getCode());
    }

    public void unassignUser(Long groupId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw CustomException.badRequest("userId는 필수이며 비어 있을 수 없습니다.", "INVALID_INPUT");
        }
        findById(groupId);
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
     * Returns per-screen read/write/approve from permission_group_screen for the user's groups.
     * Key = screen_id, value = {read, write, approve} (Boolean; null = use derivation).
     * When user has multiple groups with same screen, aggregates: write=true if any has true or null; approve=true if any has true or null.
     */
    public Map<String, ScreenFunctionFromDb> getScreenFunctionsForUser(String userId) {
        Map<String, ScreenFunctionFromDb> result = new HashMap<>();
        if (userId == null || userId.isBlank()) {
            return result;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT pgs.screen_id, pgs.read, pgs.write, pgs.approve FROM permission_group_screen pgs " +
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
                        result.merge(screenId, new ScreenFunctionFromDb(readVal, writeVal, approveVal), this::mergeScreenFunction);
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
        return new ScreenFunctionFromDb(read, write, approve);
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

    /** Per-screen read/write/approve from DB. null = use derivation. */
    public static final class ScreenFunctionFromDb {
        public final Boolean read;
        public final Boolean write;
        public final Boolean approve;

        public ScreenFunctionFromDb(Boolean read, Boolean write, Boolean approve) {
            this.read = read;
            this.write = write;
            this.approve = approve;
        }
    }

    /**
     * Returns per-screen scope for activity-log, statistics, search-history.
     * Key = screen_id, value = 'self' or 'all'. When user has multiple groups, if any has 'all', use 'all'; else 'self'.
     * NULL or missing scope in DB = 'self'.
     */
    public Map<String, String> getScreenScopesForUser(String userId) {
        Map<String, String> scopes = new HashMap<>();
        if (userId == null || userId.isBlank()) {
            return scopes;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT pgs.screen_id, pgs.scope FROM permission_group_screen pgs " +
                    "INNER JOIN app_user_permission_group aupg ON pgs.permission_group_id = aupg.permission_group_id " +
                    "WHERE aupg.user_id = ? AND pgs.screen_id IN ('activity-log', 'statistics', 'search-history')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String screenId = rs.getString("screen_id");
                        String scope = rs.getString("scope");
                        if (screenId == null || screenId.isBlank()) continue;
                        if (!ScreenConstants.supportsScope(screenId)) continue;
                        String effective = "all".equalsIgnoreCase(scope) ? "all" : "self";
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
            String sql = "SELECT u.username, u.role, u.department_code, u.position, u.rank, u.is_system_admin FROM app_user u " +
                    "INNER JOIN app_user_permission_group a ON u.username = a.user_id WHERE a.permission_group_id = ? ORDER BY u.username";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String username = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        String position = rs.getString("position");
                        String rank = rs.getString("rank");
                        boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        list.add(new UserListItemResponse(username, role, departmentCode, false, position, rank, isSystemAdmin));
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
            String sql = "SELECT 1 FROM app_user WHERE username = ? LIMIT 1";
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

    private void validateAllowedScreens(List<AllowedScreenItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (AllowedScreenItem item : items) {
            if (item == null || item.getScreenId() == null || item.getScreenId().isBlank()) continue;
            String screenId = item.getScreenId().trim();
            if (!ScreenConstants.isValid(screenId)) {
                throw CustomException.badRequest("유효하지 않은 화면 ID입니다: " + screenId, "INVALID_SCREEN_ID");
            }
            if (ScreenConstants.supportsScope(screenId)) {
                String scope = item.getScope();
                if (scope != null && !scope.isBlank() && !"self".equalsIgnoreCase(scope) && !"all".equalsIgnoreCase(scope)) {
                    throw CustomException.badRequest("scope는 'self' 또는 'all'이어야 합니다: " + scope, "INVALID_INPUT");
                }
            }
            validateScreenFunctions(screenId, item.getRead(), item.getWrite(), item.getApprove());
        }
    }

    /**
     * Validates read/write/approve per screen per §1.1.1. main: read-only; write/approve only on supported screens.
     */
    private void validateScreenFunctions(String screenId, Boolean read, Boolean write, Boolean approve) {
        if (ScreenConstants.MAIN.equals(screenId)) {
            if (Boolean.TRUE.equals(write) || Boolean.TRUE.equals(approve)) {
                throw CustomException.badRequest("main 화면은 조회만 지원합니다. write 또는 approve를 지정할 수 없습니다.", "INVALID_SCREEN_FUNCTION");
            }
        }
        if (Boolean.TRUE.equals(write) && !ScreenConstants.supportsWrite(screenId)) {
            throw CustomException.badRequest("해당 화면은 write를 지원하지 않습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
        }
        if (Boolean.TRUE.equals(approve) && !ScreenConstants.supportsApprove(screenId)) {
            throw CustomException.badRequest("해당 화면은 approve를 지원하지 않습니다: " + screenId, "INVALID_SCREEN_FUNCTION");
        }
    }

    private List<AllowedScreenItem> loadAllowedScreens(Connection conn, long groupId) throws SQLException {
        List<AllowedScreenItem> screens = new ArrayList<>();
        String sql = "SELECT screen_id, scope, read, write, approve FROM permission_group_screen WHERE permission_group_id = ? ORDER BY screen_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String screenId = rs.getString("screen_id");
                    String scope = rs.getString("scope");
                    if (screenId != null && !screenId.isBlank()) {
                        AllowedScreenItem item = new AllowedScreenItem();
                        item.setScreenId(screenId);
                        if (ScreenConstants.supportsScope(screenId) && scope != null && !scope.isBlank()) {
                            item.setScope("all".equalsIgnoreCase(scope) ? "all" : "self");
                        }
                        Boolean readVal = rs.getObject("read", Boolean.class);
                        Boolean writeVal = rs.getObject("write", Boolean.class);
                        Boolean approveVal = rs.getObject("approve", Boolean.class);
                        if (readVal != null) item.setRead(readVal);
                        if (writeVal != null) item.setWrite(writeVal);
                        if (approveVal != null) item.setApprove(approveVal);
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
            String insertSql = "INSERT INTO permission_group_screen (permission_group_id, screen_id, scope, read, write, approve) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (AllowedScreenItem item : items) {
                    if (item == null || item.getScreenId() == null || item.getScreenId().isBlank()) continue;
                    String screenId = item.getScreenId().trim();
                    ps.setLong(1, groupId);
                    ps.setString(2, screenId);
                    String scope = null;
                    if (ScreenConstants.supportsScope(screenId)) {
                        String s = item.getScope();
                        scope = "all".equalsIgnoreCase(s) ? "all" : "self";
                    }
                    ps.setString(3, scope);
                    ps.setObject(4, item.getRead());
                    ps.setObject(5, item.getWrite());
                    ps.setObject(6, item.getApprove());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }
}
