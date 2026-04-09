package com.logmng.service;

import com.logmng.exception.CustomException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Active {@code app_user.employee_number} uniqueness — same predicate as
 * {@link UserManagementV2Service} direct create (non-null values only; soft-deleted rows ignored).
 */
final class AppUserEmployeeNumberUniqueness {

    private AppUserEmployeeNumberUniqueness() {
    }

    /**
     * @throws com.logmng.exception.CustomException HTTP 409 {@code USER_EMPLOYEE_NUMBER_DUPLICATED} if another
     *                                              active user already has this trimmed employee number
     */
    static void ensureAvailableForActiveUser(Connection conn, String employeeNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM app_user WHERE employee_number = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, employeeNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw CustomException.conflict("이미 등록된 사번입니다.", "USER_EMPLOYEE_NUMBER_DUPLICATED");
                }
            }
        }
    }
}
