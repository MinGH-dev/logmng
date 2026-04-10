package com.logmng.repository;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence for {@code screen_display_label}. Implemented by JDBC (production); mockable in tests.
 */
public interface ScreenDisplayLabelDataAccess {

    List<ScreenDisplayLabelRow> findAllOrdered() throws SQLException;

    void upsertAll(List<ScreenDisplayLabelRow> rows) throws SQLException;
}
