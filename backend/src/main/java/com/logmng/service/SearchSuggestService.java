package com.logmng.service;

import com.logmng.dto.response.FieldMetadataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * 검색 추천 서비스
 */
@Service
public class SearchSuggestService {
    
    private static final Logger log = LoggerFactory.getLogger(SearchSuggestService.class);
    private final DataSource imagelogDataSource;
    private final FieldMetadataService fieldMetadataService;
    
    public SearchSuggestService(@Qualifier("imagelogDataSource") DataSource imagelogDataSource,
                                FieldMetadataService fieldMetadataService) {
        this.imagelogDataSource = imagelogDataSource;
        this.fieldMetadataService = fieldMetadataService;
    }
    
    /**
     * 추천 목록 조회
     */
    public List<Map<String, Object>> getSuggestions(String logType, String context, 
                                                     String prefix, String fieldName) {
        log.debug("추천 목록 조회: logType={}, context={}, prefix={}, fieldName={}", 
                logType, context, prefix, fieldName);
        
        if (!"java_fw_imglog".equals(logType)) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        if ("field".equals(context)) {
            // 필드명 추천
            suggestions = getFieldSuggestions(prefix);
        } else if ("operator".equals(context)) {
            // 연산자 추천
            suggestions = getOperatorSuggestions(fieldName);
        } else if ("value".equals(context)) {
            // 값 추천
            suggestions = getValueSuggestions(fieldName, prefix);
        }
        
        return suggestions;
    }
    
    /**
     * 필드명 추천
     */
    private List<Map<String, Object>> getFieldSuggestions(String prefix) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        List<FieldMetadataResponse> fields = fieldMetadataService.getFieldMetadata("java_fw_imglog");
        
        String lowerPrefix = (prefix != null ? prefix.toLowerCase() : "");
        
        for (FieldMetadataResponse field : fields) {
            String fieldName = field.getName();
            String label = field.getLabel();
            
            // prefix 매칭
            if (lowerPrefix.isEmpty() || 
                fieldName.toLowerCase().startsWith(lowerPrefix) ||
                label.toLowerCase().contains(lowerPrefix)) {
                
                Map<String, Object> suggestion = new HashMap<>();
                suggestion.put("label", label);
                suggestion.put("value", fieldName);
                suggestion.put("type", field.getType());
                suggestion.put("description", field.getLabel());
                suggestions.add(suggestion);
            }
        }
        
        return suggestions;
    }
    
    /**
     * 연산자 추천
     */
    private List<Map<String, Object>> getOperatorSuggestions(String fieldName) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        if (fieldName == null || fieldName.isEmpty()) {
            return suggestions;
        }
        
        List<FieldMetadataResponse> fields = fieldMetadataService.getFieldMetadata("java_fw_imglog");
        FieldMetadataResponse field = fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
        
        if (field == null) {
            return suggestions;
        }
        
        List<String> operators = field.getOperatorsAllowed();
        for (String op : operators) {
            Map<String, Object> suggestion = new HashMap<>();
            suggestion.put("label", getOperatorLabel(op));
            suggestion.put("value", op);
            suggestion.put("description", getOperatorDescription(op));
            suggestions.add(suggestion);
        }
        
        return suggestions;
    }
    
    /**
     * 값 추천
     */
    private List<Map<String, Object>> getValueSuggestions(String fieldName, String prefix) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        if (fieldName == null || fieldName.isEmpty()) {
            return suggestions;
        }
        
        List<FieldMetadataResponse> fields = fieldMetadataService.getFieldMetadata("java_fw_imglog");
        FieldMetadataResponse field = fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
        
        if (field == null) {
            return suggestions;
        }
        
        String valueSource = field.getValueSource();
        
        if ("enum".equals(valueSource)) {
            // enum 값 추천
            List<String> enumValues = field.getEnumValues();
            if (enumValues != null) {
                String lowerPrefix = (prefix != null ? prefix.toLowerCase() : "");
                for (String value : enumValues) {
                    if (lowerPrefix.isEmpty() || value.toLowerCase().startsWith(lowerPrefix)) {
                        Map<String, Object> suggestion = new HashMap<>();
                        suggestion.put("label", value);
                        suggestion.put("value", value);
                        suggestions.add(suggestion);
                    }
                }
            }
        } else if ("api".equals(valueSource)) {
            // DB에서 값 추천 (최근 사용 값 또는 빈도 기반)
            suggestions = getValueSuggestionsFromDb(fieldName, prefix);
        }
        
        return suggestions;
    }
    
    /**
     * DB에서 값 추천 조회
     */
    private List<Map<String, Object>> getValueSuggestionsFromDb(String fieldName, String prefix) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            String sql = "SELECT DISTINCT " + fieldName + " FROM imagelog " +
                        "WHERE " + fieldName + " IS NOT NULL ";
            
            List<Object> params = new ArrayList<>();
            
            if (prefix != null && !prefix.isEmpty()) {
                sql += "AND " + fieldName + " LIKE ? ";
                params.add(prefix + "%");
            }
            
            sql += "ORDER BY " + fieldName + " LIMIT 20";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String value = rs.getString(1);
                        if (value != null) {
                            Map<String, Object> suggestion = new HashMap<>();
                            suggestion.put("label", value);
                            suggestion.put("value", value);
                            suggestions.add(suggestion);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("값 추천 조회 중 오류 발생: fieldName={}, prefix={}", fieldName, prefix, e);
        }
        
        return suggestions;
    }
    
    /**
     * 연산자 라벨
     */
    private String getOperatorLabel(String operator) {
        switch (operator) {
            case ":": return "포함";
            case "=": return "일치";
            case ">=": return "이상";
            case "<=": return "이하";
            case ">": return "초과";
            case "<": return "미만";
            case "IN": return "포함 (다중값)";
            case "NOT IN": return "제외 (다중값)";
            case "~": return "부분일치";
            default: return operator;
        }
    }
    
    /**
     * 연산자 설명
     */
    private String getOperatorDescription(String operator) {
        switch (operator) {
            case ":": return "값이 포함된 경우";
            case "=": return "값이 정확히 일치하는 경우";
            case ">=": return "값이 이상인 경우";
            case "<=": return "값이 이하인 경우";
            case ">": return "값이 초과인 경우";
            case "<": return "값이 미만인 경우";
            case "IN": return "값이 리스트에 포함된 경우";
            case "NOT IN": return "값이 리스트에 포함되지 않은 경우";
            case "~": return "값이 부분적으로 일치하는 경우";
            default: return "";
        }
    }
}





