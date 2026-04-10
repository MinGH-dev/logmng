package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.testsupport.H2ClasspathSql;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routing: PB FEP paths use {@code pbDataSource}; ImageLog paths use {@code imagelogDataSource}.
 * When {@code pbDataSource} is the same bean as primary, PB traffic shares the primary pool.
 * DML for fixtures is in classpath SQL under {@code sql/logdb-routing/} (not in Java literals).
 */
class LogDbServiceDataSourceRoutingTest {

    private static final String H2_COMBINED =
            "jdbc:h2:mem:logdb_routing_combined;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String H2_PRIMARY_ONLY =
            "jdbc:h2:mem:logdb_routing_pri;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String H2_PB_ONLY =
            "jdbc:h2:mem:logdb_routing_pb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long ILOG_SEED_MS = 1_730_000_000_000L;

    private static CryptoUtil testCrypto() throws Exception {
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        return cryptoUtil;
    }

    private static void createPbTables(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS pb_send (" +
                "id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50)," +
                "user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB," +
                "status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50)," +
                "created_at TIMESTAMP, updated_at TIMESTAMP)");
        stmt.execute("CREATE TABLE IF NOT EXISTS pb_recv (" +
                "id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50)," +
                "user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB," +
                "status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50)," +
                "created_at TIMESTAMP, updated_at TIMESTAMP)");
    }

    private static void createImagelogTable(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS imagelog (" +
                "application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256)," +
                "data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT)");
    }

    private static DataSource h2DataSource(String url) {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(url);
        return ds;
    }

    private static DataSource createCombinedH2() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_COMBINED);
             Statement stmt = conn.createStatement()) {
            createImagelogTable(stmt);
            createPbTables(stmt);
        }
        return h2DataSource(H2_COMBINED);
    }

    private static DataSource createPrimaryOnlyH2() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_PRIMARY_ONLY);
             Statement stmt = conn.createStatement()) {
            createImagelogTable(stmt);
        }
        return h2DataSource(H2_PRIMARY_ONLY);
    }

    private static DataSource createPbOnlyH2() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_PB_ONLY);
             Statement stmt = conn.createStatement()) {
            createPbTables(stmt);
        }
        return h2DataSource(H2_PB_ONLY);
    }

    private static DataSource counting(DataSource delegate, AtomicInteger counter) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                counter.incrementAndGet();
                return delegate.getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws java.sql.SQLException {
                counter.incrementAndGet();
                return delegate.getConnection(username, password);
            }

            @Override
            public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
                return delegate.getLogWriter();
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) throws java.sql.SQLException {
                delegate.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws java.sql.SQLException {
                delegate.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws java.sql.SQLException {
                return delegate.getLoginTimeout();
            }

            @Override
            public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
                return delegate.getParentLogger();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                return delegate.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
                return delegate.isWrapperFor(iface);
            }
        };
    }

    @Test
    void pbFeplogSearch_whenPbReusesPrimary_usesPrimaryPoolOnly() throws Exception {
        DataSource raw = createCombinedH2();
        AtomicInteger primaryConnCount = new AtomicInteger();
        AtomicInteger imagelogConnCount = new AtomicInteger();
        DataSource primaryDs = counting(raw, primaryConnCount);
        DataSource pbDs = primaryDs;
        DataSource imagelogDs = counting(raw, imagelogConnCount);
        LogDbService logDbService = new LogDbService(primaryDs, pbDs, imagelogDs, testCrypto());

        try (Connection c = raw.getConnection()) {
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/truncate-pb.sql");
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/seed-pb-send-one.sql");
        }
        long now = System.currentTimeMillis();
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault());
        req.setStartDate(start.format(FMT));
        req.setEndDate(end.format(FMT));
        req.setPage(1);
        req.setPageSize(10);

        int beforeP = primaryConnCount.get();
        int beforeI = imagelogConnCount.get();
        logDbService.searchLogs(req);
        assertThat(primaryConnCount.get()).isGreaterThan(beforeP);
        assertThat(imagelogConnCount.get()).isEqualTo(beforeI);
    }

    @Test
    void pbFeplogSearch_whenPbDedicated_usesPbPoolOnly() throws Exception {
        DataSource rawPrimary = createPrimaryOnlyH2();
        DataSource rawPb = createPbOnlyH2();
        AtomicInteger primaryConnCount = new AtomicInteger();
        AtomicInteger pbConnCount = new AtomicInteger();
        AtomicInteger imagelogConnCount = new AtomicInteger();
        DataSource primaryDs = counting(rawPrimary, primaryConnCount);
        DataSource pbDs = counting(rawPb, pbConnCount);
        DataSource imagelogDs = counting(rawPrimary, imagelogConnCount);
        LogDbService logDbService = new LogDbService(primaryDs, pbDs, imagelogDs, testCrypto());

        try (Connection c = rawPb.getConnection()) {
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/truncate-pb.sql");
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/seed-pb-send-one.sql");
        }
        long now = System.currentTimeMillis();
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault());
        req.setStartDate(start.format(FMT));
        req.setEndDate(end.format(FMT));
        req.setPage(1);
        req.setPageSize(10);

        int beforeP = primaryConnCount.get();
        int beforePb = pbConnCount.get();
        int beforeI = imagelogConnCount.get();
        logDbService.searchLogs(req);
        assertThat(pbConnCount.get()).isGreaterThan(beforePb);
        assertThat(primaryConnCount.get()).isEqualTo(beforeP);
        assertThat(imagelogConnCount.get()).isEqualTo(beforeI);
    }

    @Test
    void javaFwImglogSearch_usesImagelogDataSourceOnly() throws Exception {
        DataSource raw = createCombinedH2();
        AtomicInteger primaryConnCount = new AtomicInteger();
        AtomicInteger imagelogConnCount = new AtomicInteger();
        DataSource primaryDs = counting(raw, primaryConnCount);
        DataSource pbDs = primaryDs;
        DataSource imagelogDs = counting(raw, imagelogConnCount);
        LogDbService logDbService = new LogDbService(primaryDs, pbDs, imagelogDs, testCrypto());

        try (Connection c = raw.getConnection()) {
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/truncate-imagelog.sql");
            H2ClasspathSql.runScript(c, "/sql/logdb-routing/seed-imagelog-one.sql");
        }
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("java_fw_imglog");
        req.setStartDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ILOG_SEED_MS - 86400000L), ZoneId.systemDefault()).format(FMT));
        req.setEndDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ILOG_SEED_MS + 86400000L), ZoneId.systemDefault()).format(FMT));
        req.setPage(1);
        req.setPageSize(10);

        int beforeP = primaryConnCount.get();
        int beforeI = imagelogConnCount.get();
        logDbService.searchLogs(req);
        assertThat(imagelogConnCount.get()).isGreaterThan(beforeI);
        assertThat(primaryConnCount.get()).isEqualTo(beforeP);
    }
}
