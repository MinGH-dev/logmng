package com.logmng.service;

import com.logmng.dto.response.DepartmentNodeResponse;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부서 계층 조회. §12.1
 */
@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DataSource dataSource;

    public DepartmentService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 부서 코드 존재 여부
     */
    public boolean existsByCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM department WHERE code = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("부서 존재 확인 실패: code={}", code, e);
            return false;
        }
    }

    /**
     * 평면 목록 (parent_code, name, sort_order)
     */
    public List<Map<String, Object>> listFlat() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT code, parent_code, name, sort_order FROM department ORDER BY parent_code NULLS FIRST, sort_order, code";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", rs.getString("code"));
                        row.put("parentCode", rs.getString("parent_code"));
                        row.put("name", rs.getString("name"));
                        row.put("sortOrder", rs.getObject("sort_order") != null ? rs.getInt("sort_order") : 0);
                        list.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("부서 목록(flat) 조회 실패", e);
            throw new RuntimeException("부서 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 트리 구조. 루트(parent_code IS NULL)부터 children 재귀.
     */
    public List<DepartmentNodeResponse> listTree() {
        List<Map<String, Object>> flat = listFlat();
        Map<String, DepartmentNodeResponse> byCode = new LinkedHashMap<>();
        for (Map<String, Object> row : flat) {
            String code = (String) row.get("code");
            String parentCode = (String) row.get("parentCode");
            String name = (String) row.get("name");
            Integer sortOrder = row.get("sortOrder") != null ? (Integer) row.get("sortOrder") : 0;
            DepartmentNodeResponse node = new DepartmentNodeResponse(code, parentCode, name, sortOrder);
            byCode.put(code, node);
        }
        for (DepartmentNodeResponse node : byCode.values()) {
            String parentCode = node.getParentCode();
            if (parentCode != null && !parentCode.isBlank()) {
                DepartmentNodeResponse parent = byCode.get(parentCode);
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        List<DepartmentNodeResponse> roots = new ArrayList<>();
        for (DepartmentNodeResponse node : byCode.values()) {
            if (node.getParentCode() == null || node.getParentCode().isBlank()) {
                roots.add(node);
            }
        }
        roots.sort((a, b) -> {
            int oa = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int ob = b.getSortOrder() != null ? b.getSortOrder() : 0;
            if (oa != ob) return Integer.compare(oa, ob);
            return (a.getCode() != null && b.getCode() != null) ? a.getCode().compareTo(b.getCode()) : 0;
        });
        return roots;
    }

    /**
     * 부서 코드로 조회 시 없으면 404
     */
    public void requireExists(String code) {
        if (!existsByCode(code)) {
            throw CustomException.notFound("부서를 찾을 수 없습니다: " + code, "DEPARTMENT_NOT_FOUND");
        }
    }

    private static final int MAX_ANCESTOR_DEPTH = 50;

    /**
     * 해당 부서부터 루트까지의 상위 부서 코드 목록 (본인 포함). 승인 권한 판단용.
     * 예: DEPT01 -> [DEPT01, HQ]
     */
    public List<String> getAncestorCodesIncludingSelf(String code) {
        List<String> out = new ArrayList<>();
        String current = code;
        int depth = 0;
        while (current != null && !current.isBlank() && depth < MAX_ANCESTOR_DEPTH) {
            depth++;
            out.add(current);
            String parent = null;
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT parent_code FROM department WHERE code = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, current);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            parent = rs.getString("parent_code");
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("부서 상위 조회 실패: code={}", current, e);
                break;
            }
            current = parent;
        }
        return out;
    }
}
