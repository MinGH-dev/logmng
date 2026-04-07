package com.logmng.service;

import com.logmng.config.AuthProperties;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps directory principals to app_user via ext_employee + app_user_external_identity.
 */
@Service
public class ExternalIdentityService {

    private final DataSource dataSource;
    private final AuthProperties authProperties;

    public ExternalIdentityService(DataSource dataSource, AuthProperties authProperties) {
        this.dataSource = dataSource;
        this.authProperties = authProperties;
    }

    /**
     * After successful directory authentication, resolve numeric app_user.id.
     * Matches ext_employee.employee_number (and email / UPN local-part) to mapping table.
     *
     * @return null if no provisioned mapping exists
     */
    public Long findAppUserIdForDirectoryPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return null;
        }
        String p = principal.trim();
        List<String> variants = new ArrayList<>();
        variants.add(p);
        int at = p.indexOf('@');
        if (at > 0) {
            variants.add(p.substring(0, at));
        }
        for (String key : variants) {
            Long id = lookupByEmployeeKey(key);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private Long lookupByEmployeeKey(String key) {
        String sql = "SELECT m.app_user_id "
                + "FROM app_user_external_identity m "
                + "INNER JOIN ext_employee e ON e.source_system = m.source_system "
                + "  AND e.external_employee_id = m.external_employee_id "
                + "WHERE e.is_active = TRUE AND ("
                + "  e.employee_number = ? OR (e.email IS NOT NULL AND LOWER(TRIM(e.email)) = LOWER(TRIM(?)))"
                + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("app_user_id");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve external identity", e);
        }
        return null;
    }

    public String getDefaultSourceSystem() {
        String s = authProperties.getProvisioning().getDefaultSourceSystem();
        return s != null && !s.isBlank() ? s.trim() : "DEFAULT";
    }
}
