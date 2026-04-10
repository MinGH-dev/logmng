package com.logmng.util;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.DepartmentService;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves {@link UserManagementReadScopeContext} for UM v2 read APIs; stores result on the request (req 20260409).
 */
public final class UserManagementReadScopeResolver {

    public static final String REQUEST_ATTR = "com.logmng.userManagementReadScope.v1";

    private UserManagementReadScopeResolver() {
    }

    public static UserManagementReadScopeContext resolve(HttpServletRequest request,
                                                         LoginResponse user,
                                                         DataSource dataSource,
                                                         DepartmentService departmentService) {
        Object cached = request.getAttribute(REQUEST_ATTR);
        if (cached instanceof UserManagementReadScopeContext) {
            return (UserManagementReadScopeContext) cached;
        }
        UserManagementReadScopeContext ctx = build(user, dataSource, departmentService);
        request.setAttribute(REQUEST_ATTR, ctx);
        return ctx;
    }

    static UserManagementReadScopeContext build(LoginResponse user,
                                               DataSource dataSource,
                                               DepartmentService departmentService) {
        if (user == null) {
            return UserManagementReadScopeContext.unrestricted();
        }
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            return UserManagementReadScopeContext.unrestricted();
        }
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed == null || !allowed.contains(ScreenConstants.USER_MANAGEMENT_V2)) {
            return UserManagementReadScopeContext.unrestricted();
        }
        String scope = ScopeHelper.resolveScope(ScreenConstants.USER_MANAGEMENT_V2, false, user.getScreenScopes());
        Long uid = user.getUserId();
        if (uid == null && user.getUsername() != null && !user.getUsername().isBlank()) {
            uid = loadNumericUserIdByUsername(dataSource, user.getUsername().trim());
        }
        if ("all".equalsIgnoreCase(scope)) {
            return new UserManagementReadScopeContext(true, "all", null, null, false);
        }
        if (uid == null) {
            return new UserManagementReadScopeContext(true, scope, Collections.emptyList(),
                    Collections.emptySet(), true);
        }
        if ("self".equalsIgnoreCase(scope)) {
            List<Long> ids = Collections.singletonList(uid);
            Set<String> deptCodes = visibleDepartmentsForSelf(dataSource, departmentService, uid);
            return new UserManagementReadScopeContext(true, "self", ids, deptCodes, true);
        }
        if ("team".equalsIgnoreCase(scope)) {
            List<Long> teamIds = DepartmentScopeHelper.getNumericUserIdsInSameDepartment(dataSource, uid);
            Set<String> deptCodes = visibleDepartmentsForTeam(dataSource, departmentService, uid);
            return new UserManagementReadScopeContext(true, "team", teamIds, deptCodes, true);
        }
        return UserManagementReadScopeContext.unrestricted();
    }

    private static Long loadNumericUserIdByUsername(DataSource dataSource, String username) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject("id", Long.class);
                    }
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    private static Set<String> visibleDepartmentsForSelf(DataSource dataSource,
                                                         DepartmentService departmentService,
                                                         Long userId) {
        String dept = loadDepartmentCodeForUser(dataSource, userId);
        if (dept == null || dept.isBlank()) {
            return Collections.singleton(com.logmng.service.UserPermissionHierarchyService.UNASSIGNED_DEPARTMENT_CODE);
        }
        List<String> upward = departmentService.getAncestorCodesIncludingSelf(dept.trim());
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = upward.size() - 1; i >= 0; i--) {
            out.add(upward.get(i));
        }
        return out;
    }

    private static Set<String> visibleDepartmentsForTeam(DataSource dataSource,
                                                         DepartmentService departmentService,
                                                         Long userId) {
        String dept = loadDepartmentCodeForUser(dataSource, userId);
        if (dept == null || dept.isBlank()) {
            return Collections.singleton(com.logmng.service.UserPermissionHierarchyService.UNASSIGNED_DEPARTMENT_CODE);
        }
        String d = dept.trim();
        Set<String> out = new HashSet<>();
        List<String> upward = departmentService.getAncestorCodesIncludingSelf(d);
        for (int i = upward.size() - 1; i >= 0; i--) {
            out.add(upward.get(i));
        }
        out.addAll(departmentService.getDescendantCodesIncludingSelf(d));
        return out;
    }

    private static String loadDepartmentCodeForUser(DataSource dataSource, Long userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT department_code FROM app_user WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
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

    /**
     * Team-scope mutation: target department must be same code as actor or under actor's department subtree.
     */
    public static boolean isDepartmentAllowedForTeamMutation(DepartmentService departmentService,
                                                             String actorDepartmentCode,
                                                             String targetDepartmentCode) {
        if (targetDepartmentCode == null || targetDepartmentCode.isBlank()) {
            return false;
        }
        String t = targetDepartmentCode.trim();
        if (actorDepartmentCode == null || actorDepartmentCode.isBlank()) {
            return false;
        }
        String a = actorDepartmentCode.trim();
        if (a.equalsIgnoreCase(t)) {
            return true;
        }
        return departmentService.isStrictDescendantOf(t, a);
    }

    /**
     * Self-scope mutation: only actor's own department row (exact code match).
     */
    public static boolean isDepartmentAllowedForSelfMutation(String actorDepartmentCode, String targetDepartmentCode) {
        if (actorDepartmentCode == null || targetDepartmentCode == null) {
            return false;
        }
        return actorDepartmentCode.trim().equalsIgnoreCase(targetDepartmentCode.trim());
    }
}
