package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-01 / TC-02 routing: PB paths use primary {@link DataSource}; ImageLog paths use imagelog {@link DataSource}.
 */
class LogDbServiceDataSourceRoutingTest {

    private static final String H2_URL = "jdbc:h2:mem:logdb_routing;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DataSource raw;
    private AtomicInteger primaryConnCount;
    private AtomicInteger imagelogConnCount;
    private DataSource primaryDs;
    private DataSource imagelogDs;
    private LogDbService logDbService;

    @BeforeEach
    void setUp() throws Exception {
        raw = createH2DataSource();
        primaryConnCount = new AtomicInteger();
        imagelogConnCount = new AtomicInteger();
        primaryDs = counting(raw, primaryConnCount);
        imagelogDs = counting(raw, imagelogConnCount);
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        logDbService = new LogDbService(primaryDs, imagelogDs, cryptoUtil);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS imagelog (" +
                    "application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256)," +
                    "data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT)");
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
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
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
    void pbFeplogSearch_usesPrimaryDataSourceOnly() throws Exception {
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("TRUNCATE TABLE pb_send");
            s.executeUpdate("TRUNCATE TABLE pb_recv");
            s.executeUpdate("INSERT INTO pb_send (id, log_timestamp, media_code, tr_code, user_id) VALUES (1, CURRENT_TIMESTAMP, 'M','T','u')");
        }
        long now = System.currentTimeMillis();
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        LocalDateTime start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault());
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
    void javaFwImglogSearch_usesImagelogDataSourceOnly() throws Exception {
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("TRUNCATE TABLE imagelog");
            s.executeUpdate("INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) " +
                    "VALUES ('a','b','c','ok','{}','d','g1','{}','h', " + System.currentTimeMillis() + ")");
        }
        long ts = System.currentTimeMillis();
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("java_fw_imglog");
        req.setStartDate(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts - 86400000L), ZoneId.systemDefault()).format(FMT));
        req.setEndDate(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts + 86400000L), ZoneId.systemDefault()).format(FMT));
        req.setPage(1);
        req.setPageSize(10);

        int beforeP = primaryConnCount.get();
        int beforeI = imagelogConnCount.get();
        logDbService.searchLogs(req);
        assertThat(imagelogConnCount.get()).isGreaterThan(beforeI);
        assertThat(primaryConnCount.get()).isEqualTo(beforeP);
    }
}
