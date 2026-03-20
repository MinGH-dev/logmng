package com.logmng.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Primary JDBC (A): system + PB log. Secondary JDBC (B): ImageLog ({@code imagelog}) only.
 * <p>
 * Dev fallback: if {@code app.datasource.imagelog.url} is empty, ImageLog reuses the primary
 * {@link DataSource} (same pool). Documented in {@code application.yml}.
 * <p>
 * TC-04 / prod: when a dedicated ImageLog URL is set, the ImageLog pool uses Hikari
 * {@code initializationFailTimeout} so startup fails fast if B is unreachable (clear ops signal).
 * Do not log full JDBC URLs or passwords at INFO (see {@link #describePool(DataSource)}).
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String primaryJdbcUrl;

    @Value("${spring.datasource.username}")
    private String primaryUsername;

    @Value("${spring.datasource.password}")
    private String primaryPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String primaryDriverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${app.db.schema.sys:public}")
    private String schemaSys;

    @Value("${app.db.schema.pb:public}")
    private String schemaPb;

    @Value("${app.db.schema.imagelog:public}")
    private String schemaImagelog;

    @Value("${app.datasource.imagelog.url:}")
    private String imagelogJdbcUrl;

    @Value("${app.datasource.imagelog.username:}")
    private String imagelogUsername;

    @Value("${app.datasource.imagelog.password:}")
    private String imagelogPassword;

    @Value("${app.datasource.imagelog.driver-class-name:}")
    private String imagelogDriverClassName;

    @Value("${app.datasource.imagelog.fail-fast:true}")
    private boolean imagelogFailFast;

    @Value("${app.datasource.imagelog.initialization-fail-timeout-ms:30000}")
    private long imagelogInitFailTimeoutMs;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(primaryJdbcUrl);
        config.setUsername(primaryUsername);
        config.setPassword(primaryPassword);
        config.setDriverClassName(primaryDriverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName("LogMngHikariPool-primary");
        config.setConnectionInitSql(PgSchemaSupport.buildPrimarySearchPathInitSql(schemaSys, schemaPb));
        HikariDataSource ds = new HikariDataSource(config);
        log.info("Primary datasource ready: {}", describePool(ds));
        return ds;
    }

    /**
     * ImageLog-only datasource. Falls back to primary when {@code app.datasource.imagelog.url} is blank.
     */
    @Bean(name = "imagelogDataSource")
    public DataSource imagelogDataSource(@Qualifier("dataSource") DataSource primaryDataSource) {
        String url = imagelogJdbcUrl != null ? imagelogJdbcUrl.trim() : "";
        if (url.isEmpty()) {
            log.info("ImageLog datasource: using primary pool (app.datasource.imagelog.url not set; dev single-DB fallback).");
            return primaryDataSource;
        }
        if (url.equals(primaryJdbcUrl)) {
            log.info("ImageLog datasource: same URL as primary; reusing primary pool bean.");
            return primaryDataSource;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(blankToDefault(imagelogUsername, primaryUsername));
        config.setPassword(blankToDefault(imagelogPassword, primaryPassword));
        String driver = blankToDefault(imagelogDriverClassName, primaryDriverClassName);
        config.setDriverClassName(driver);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName("LogMngHikariPool-imagelog");
        config.setConnectionInitSql(PgSchemaSupport.buildImagelogSearchPathInitSql(schemaImagelog));
        if (imagelogFailFast) {
            config.setInitializationFailTimeout(imagelogInitFailTimeoutMs);
        } else {
            config.setInitializationFailTimeout(-1);
        }

        HikariDataSource ds = new HikariDataSource(config);
        log.info("ImageLog datasource ready (dedicated pool): {}", describePool(ds));
        return ds;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "imagelogJdbcTemplate")
    public JdbcTemplate imagelogJdbcTemplate(@Qualifier("imagelogDataSource") DataSource imagelogDataSource) {
        return new JdbcTemplate(imagelogDataSource);
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    /**
     * Safe summary for logs: pool name and driver only (no URL/credentials).
     */
    static String describePool(DataSource ds) {
        if (ds instanceof HikariDataSource) {
            HikariDataSource h = (HikariDataSource) ds;
            return "pool=" + h.getPoolName() + ", driver=" + h.getDriverClassName();
        }
        return "class=" + ds.getClass().getSimpleName();
    }
}
