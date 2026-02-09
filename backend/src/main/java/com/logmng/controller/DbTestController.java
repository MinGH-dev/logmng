package com.logmng.controller;

import com.logmng.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * 데이터베이스 연결 테스트
     * GET /api/db/test
     */
    @GetMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection() {
        log.debug("데이터베이스 연결 테스트 요청");
        
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            result.put("connected", true);
            result.put("databaseProductName", metaData.getDatabaseProductName());
            result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            result.put("url", metaData.getURL());
            result.put("username", metaData.getUserName());
            result.put("readOnly", connection.isReadOnly());
            result.put("autoCommit", connection.getAutoCommit());
            
            // 테이블 존재 확인
            try (ResultSet tables = metaData.getTables(null, null, "pb_send", null)) {
                result.put("pb_send_table_exists", tables.next());
            }
            
            try (ResultSet tables = metaData.getTables(null, null, "pb_recv", null)) {
                result.put("pb_recv_table_exists", tables.next());
            }
            
            // 데이터 개수 확인
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM pb_send")) {
                if (rs.next()) {
                    result.put("pb_send_count", rs.getInt(1));
                }
            }
            
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM pb_recv")) {
                if (rs.next()) {
                    result.put("pb_recv_count", rs.getInt(1));
                }
            }
            
            log.info("✅ 데이터베이스 연결 성공: {}", metaData.getDatabaseProductName());
            
        } catch (Exception e) {
            log.error("❌ 데이터베이스 연결 실패", e);
            result.put("connected", false);
            result.put("error", e.getMessage());
            result.put("errorClass", e.getClass().getName());
        }
        
        ApiResponse<Map<String, Object>> response = ApiResponse.success(result);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 테이블 스키마 정보 조회
     * GET /api/db/schema
     */
    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchema() {
        log.debug("테이블 스키마 정보 조회 요청");
        
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // pb_send 테이블 컬럼 정보
            Map<String, Object> sendColumns = new HashMap<>();
            try (ResultSet columns = metaData.getColumns(null, null, "pb_send", null)) {
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
            
            // pb_recv 테이블 컬럼 정보
            Map<String, Object> recvColumns = new HashMap<>();
            try (ResultSet columns = metaData.getColumns(null, null, "pb_recv", null)) {
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

