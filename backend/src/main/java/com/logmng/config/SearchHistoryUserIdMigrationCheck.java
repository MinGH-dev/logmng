package com.logmng.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Startup check: if search_history.user_id is not BIGINT, log a clear WARN so operators
 * run migrate-search-history-user-id-to-bigint.sql (decrypt approval checks require BIGINT).
 */
@Component
@Order(100)
public class SearchHistoryUserIdMigrationCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryUserIdMigrationCheck.class);

    private final DataSource dataSource;

    public SearchHistoryUserIdMigrationCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "SELECT data_type FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'search_history' AND column_name = 'user_id'";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (!rs.next()) {
                    log.debug("search_history table or user_id column not found; skipping migration check");
                    return;
                }
                String dataType = rs.getString("data_type");
                if (dataType == null || !dataType.equalsIgnoreCase("bigint")) {
                    log.warn("search_history.user_id is not BIGINT (current: {}). " +
                            "For decrypt approval to work, run: psql ... -f backend/src/main/resources/db/migrate-search-history-user-id-to-bigint.sql " +
                            "See backend/DB_SETUP_GUIDE.md", dataType);
                }
            }
        } catch (SQLException e) {
            log.debug("Search history user_id migration check failed (non-fatal): {}", e.getMessage());
        }
    }
}
