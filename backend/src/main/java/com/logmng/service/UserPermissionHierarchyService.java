package com.logmng.service;

import com.logmng.dto.response.DepartmentNodeResponse;
import com.logmng.dto.response.DepartmentNodeWithUsersResponse;
import com.logmng.dto.response.PermissionGroupSummary;
import com.logmng.dto.response.UserPermissionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-permission hierarchy: department tree with users and permission groups per node. §14.9.
 * Reuses DepartmentService.listTree() / listFlat(); attaches users and groups per department.
 */
@Service
public class UserPermissionHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionHierarchyService.class);

    private final DataSource dataSource;
    private final DepartmentService departmentService;

    public UserPermissionHierarchyService(DataSource dataSource, DepartmentService departmentService) {
        this.dataSource = dataSource;
        this.departmentService = departmentService;
    }

    /**
     * Tree format: list of root DepartmentNodeWithUsersResponse with children and users.
     */
    public List<DepartmentNodeWithUsersResponse> getHierarchyTree() {
        List<DepartmentNodeResponse> roots = departmentService.listTree();
        Map<String, List<UserPermissionSummary>> usersByDept = loadUsersByDepartment();
        Map<String, DepartmentNodeWithUsersResponse> byCode = new LinkedHashMap<>();
        for (DepartmentNodeResponse r : roots) {
            buildNodeWithUsers(r, byCode, usersByDept);
        }
        List<DepartmentNodeWithUsersResponse> result = new ArrayList<>();
        for (DepartmentNodeResponse r : roots) {
            result.add(byCode.get(r.getCode()));
        }
        sortRoots(result);
        return result;
    }

    /**
     * Flat format: list of department nodes with users; no children.
     */
    public List<DepartmentNodeWithUsersResponse> getHierarchyFlat() {
        List<Map<String, Object>> flat = departmentService.listFlat();
        Map<String, List<UserPermissionSummary>> usersByDept = loadUsersByDepartment();
        List<DepartmentNodeWithUsersResponse> result = new ArrayList<>();
        for (Map<String, Object> row : flat) {
            String code = (String) row.get("code");
            String parentCode = (String) row.get("parentCode");
            String name = (String) row.get("name");
            Integer sortOrder = row.get("sortOrder") != null ? (Integer) row.get("sortOrder") : 0;
            DepartmentNodeWithUsersResponse node = new DepartmentNodeWithUsersResponse(code, parentCode, name, sortOrder);
            node.setUsers(usersByDept.getOrDefault(code, new ArrayList<>()));
            result.add(node);
        }
        return result;
    }

    private void buildNodeWithUsers(DepartmentNodeResponse from, Map<String, DepartmentNodeWithUsersResponse> byCode,
                                    Map<String, List<UserPermissionSummary>> usersByDept) {
        DepartmentNodeWithUsersResponse node = new DepartmentNodeWithUsersResponse(
                from.getCode(), from.getParentCode(), from.getName(), from.getSortOrder());
        node.setUsers(usersByDept.getOrDefault(from.getCode(), new ArrayList<>()));
        byCode.put(from.getCode(), node);
        for (DepartmentNodeResponse child : from.getChildren()) {
            buildNodeWithUsers(child, byCode, usersByDept);
            node.getChildren().add(byCode.get(child.getCode()));
        }
    }

    private Map<String, List<UserPermissionSummary>> loadUsersByDepartment() {
        Map<String, List<UserPermissionSummary>> usersByDept = new LinkedHashMap<>();
        Map<String, List<PermissionGroupSummary>> groupsByUser = loadPermissionGroupsByUser();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username, role, department_code, position, rank, is_system_admin FROM app_user ORDER BY username";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    String role = rs.getString("role");
                    String departmentCode = rs.getString("department_code");
                    String position = rs.getString("position");
                    String rank = rs.getString("rank");
                    boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                    String dept = (departmentCode != null && !departmentCode.isBlank()) ? departmentCode : null;
                    if (dept == null) {
                        continue;
                    }
                    List<PermissionGroupSummary> groups = groupsByUser.getOrDefault(username, new ArrayList<>());
                    UserPermissionSummary u = new UserPermissionSummary(username, role, position, rank, groups, isSystemAdmin);
                    usersByDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(u);
                }
            }
        } catch (SQLException e) {
            log.error("Load users by department failed", e);
            throw new RuntimeException("사용자 권한 계층 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return usersByDept;
    }

    private Map<String, List<PermissionGroupSummary>> loadPermissionGroupsByUser() {
        Map<String, List<PermissionGroupSummary>> byUser = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT a.user_id, pg.id, pg.code, pg.name FROM app_user_permission_group a " +
                    "INNER JOIN permission_group pg ON pg.id = a.permission_group_id ORDER BY a.user_id, pg.sort_order, pg.code";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("user_id");
                    long id = rs.getLong("id");
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(new PermissionGroupSummary(id, code, name));
                }
            }
        } catch (SQLException e) {
            log.error("Load permission groups by user failed", e);
            throw new RuntimeException("권한 그룹 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return byUser;
    }

    private static void sortRoots(List<DepartmentNodeWithUsersResponse> roots) {
        roots.sort((a, b) -> {
            int oa = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int ob = b.getSortOrder() != null ? b.getSortOrder() : 0;
            if (oa != ob) return Integer.compare(oa, ob);
            return (a.getCode() != null && b.getCode() != null) ? a.getCode().compareTo(b.getCode()) : 0;
        });
    }
}
