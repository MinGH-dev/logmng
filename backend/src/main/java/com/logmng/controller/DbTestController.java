package com.logmng.controller;

import com.logmng.config.PgSchemaSupport;
import com.logmng.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 데이터베이스 연결 테스트 컨트롤러
 */
@RestController
@RequestMapping("/api/db")
public class DbTestController {
    
    private static final Logger log = LoggerFactory.getLogger(DbTestController.class);

    private final DataSource primaryDataSource;
    private final DataSource pbDataSource;
    private final DataSource imagelogDataSource;
    private final String pbSchema;
    private final String imagelogSchema;

    public DbTestController(@Qualifier("dataSource") DataSource primaryDataSource,
                           @Qualifier("pbDataSource") DataSource pbDataSource,
                           @Qualifier("imagelogDataSource") DataSource imagelogDataSource,
                           @Value("${app.db.schema.pb:public}") String pbSchema,
                           @Value("${app.db.schema.imagelog:public}") String imagelogSchema) {
        this.primaryDataSource = primaryDataSource;
        this.pbDataSource = pbDataSource;
        this.imagelogDataSource = imagelogDataSource;
        this.pbSchema = PgSchemaSupport.requireValidSchemaName(pbSchema);
        this.imagelogSchema = PgSchemaSupport.requireValidSchemaName(imagelogSchema);
    }
    
    /**
     * 데이터베이스 연결 테스트
     * GET /api/db/test
     */
    @GetMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection() {
        log.debug("데이터베이스 연결 테스트 요청");
        
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = primaryDataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            result.put("connected", true);
            result.put("databaseProductName", metaData.getDatabaseProductName());
            result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            // Backward-compatible key; value is masked if user:pass@ appears in URL (TC-04 / security).
            result.put("url", maskPossibleUserInfo(metaData.getURL()));
            result.put("username", metaData.getUserName());
            result.put("readOnly", connection.isReadOnly());
            result.put("autoCommit", connection.getAutoCommit());

            log.debug("Primary DB connection OK: {}", metaData.getDatabaseProductName());
            
        } catch (Exception e) {
            log.error("❌ 데이터베이스 연결 실패", e);
            result.put("connected", false);
            result.put("error", e.getMessage());
            result.put("errorClass", e.getClass().getName());
        }

        boolean pbUsesPrimaryFallback = primaryDataSource == pbDataSource;
        result.put("pbUsesPrimaryFallback", pbUsesPrimaryFallback);
        try (Connection pbConn = pbDataSource.getConnection()) {
            DatabaseMetaData pbMeta = pbConn.getMetaData();
            try (ResultSet tables = pbMeta.getTables(null, pbSchema, "pb_send", null)) {
                result.put("pb_send_table_exists", tables.next());
            }
            try (ResultSet tables = pbMeta.getTables(null, pbSchema, "pb_recv", null)) {
                result.put("pb_recv_table_exists", tables.next());
            }
            try (var stmt = pbConn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM pb_send")) {
                if (rs.next()) {
                    result.put("pb_send_count", rs.getInt(1));
                }
            }
            try (var stmt = pbConn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM pb_recv")) {
                if (rs.next()) {
                    result.put("pb_recv_count", rs.getInt(1));
                }
            }
        } catch (Exception e) {
            log.warn("PB FEP datasource probe failed (non-fatal for this endpoint): {}", e.getMessage());
            result.put("pb_send_table_exists", false);
            result.put("pb_recv_table_exists", false);
            result.put("pb_probe_error", e.getMessage());
        }

        boolean imagelogUsesPrimaryFallback = primaryDataSource == imagelogDataSource;
        result.put("imagelogUsesPrimaryFallback", imagelogUsesPrimaryFallback);
        Map<String, Object> imagelogProbe = probeImagelog();
        result.put("imagelog", imagelogProbe);
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(result);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> probeImagelog() {
        Map<String, Object> m = new HashMap<>();
        try (Connection connection = imagelogDataSource.getConnection()) {
            m.put("connected", true);
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, imagelogSchema, "imagelog", null)) {
                m.put("imagelog_table_exists", tables.next());
            }
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM imagelog")) {
                if (rs.next()) {
                    m.put("imagelog_row_count", rs.getInt(1));
                }
            }
        } catch (Exception e) {
            log.warn("ImageLog datasource probe failed (non-fatal for this endpoint): {}", e.getMessage());
            m.put("connected", false);
            m.put("error", e.getMessage());
            m.put("errorClass", e.getClass().getName());
        }
        return m;
    }

    /**
     * Strips user:password@ from JDBC URLs if present (never log or return raw credentials).
     */
    static String maskPossibleUserInfo(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("://([^:/@]+):([^@/]+)@", "://$1:***@");
    }
    
    /**
     * 테이블 스키마 정보 조회
     * GET /api/db/schema
     */
    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchema() {
        log.debug("테이블 스키마 정보 조회 요청");
        
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = pbDataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            Map<String, Object> sendColumns = new HashMap<>();
            try (ResultSet columns = metaData.getColumns(null, pbSchema, "pb_send", null)) {
                while (columns.next()) {
                    Map<String, Object> columnInfo = new HashMap<>();
                    columnInfo.put("name", columns.getString("COLUMN_NAME"));
                    columnInfo.put("type", columns.getString("TYPE_NAME"));
                    columnInfo.put("size", columns.getInt("COLUMN_SIZE"));
                    columnInfo.put("nullable", columns.getInt("NULLABLE") == 1);
                    sendColumns.put(columns.getString("COLUMN_NAME"), columnInfo);
                }
            }
            result.put("pb_send_columns", sendColumns);
            
            Map<String, Object> recvColumns = new HashMap<>();
            try (ResultSet columns = metaData.getColumns(null, pbSchema, "pb_recv", null)) {
                while (columns.next()) {
                    Map<String, Object> columnInfo = new HashMap<>();
                    columnInfo.put("name", columns.getString("COLUMN_NAME"));
                    columnInfo.put("type", columns.getString("TYPE_NAME"));
                    columnInfo.put("size", columns.getInt("COLUMN_SIZE"));
                    columnInfo.put("nullable", columns.getInt("NULLABLE") == 1);
                    recvColumns.put(columns.getString("COLUMN_NAME"), columnInfo);
                }
            }
            result.put("pb_recv_columns", recvColumns);
            
        } catch (Exception e) {
            log.error("스키마 정보 조회 실패", e);
            result.put("error", e.getMessage());
        }
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(result);
        return ResponseEntity.ok(response);
    }
}
