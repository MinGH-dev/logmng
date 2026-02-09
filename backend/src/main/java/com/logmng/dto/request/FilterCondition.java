package com.logmng.dto.request;

import java.util.List;

/**
 * 필터 조건 DTO (AST 기반)
 */
public class FilterCondition {
    private String field;
    private String operator;
    private Object value; // String 또는 List<String>
    
    public FilterCondition() {
    }
    
    public FilterCondition(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }
    
    // Getters and Setters
    public String getField() {
        return field;
    }
    
    public void setField(String field) {
        this.field = field;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public Object getValue() {
        return value;
    }
    
    public void setValue(Object value) {
        this.value = value;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getValueAsList() {
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }
    
    public String getValueAsString() {
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}





