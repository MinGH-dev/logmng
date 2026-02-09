package com.logmng.dto.response;

import java.util.List;

/**
 * 필드 메타데이터 응답 DTO
 */
public class FieldMetadataResponse {
    private String name;
    private String label;
    private String type;
    private List<String> operatorsAllowed;
    private boolean isSortable;
    private boolean isFacetable;
    private String valueSource;
    private List<String> enumValues;
    private String suggestApi;
    private boolean isEncrypted;
    
    public FieldMetadataResponse() {
    }
    
    public FieldMetadataResponse(String name, String label, String type, 
                                List<String> operatorsAllowed, boolean isSortable, 
                                boolean isFacetable, String valueSource) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.operatorsAllowed = operatorsAllowed;
        this.isSortable = isSortable;
        this.isFacetable = isFacetable;
        this.valueSource = valueSource;
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public List<String> getOperatorsAllowed() {
        return operatorsAllowed;
    }
    
    public void setOperatorsAllowed(List<String> operatorsAllowed) {
        this.operatorsAllowed = operatorsAllowed;
    }
    
    public boolean isSortable() {
        return isSortable;
    }
    
    public void setSortable(boolean sortable) {
        isSortable = sortable;
    }
    
    public boolean isFacetable() {
        return isFacetable;
    }
    
    public void setFacetable(boolean facetable) {
        isFacetable = facetable;
    }
    
    public String getValueSource() {
        return valueSource;
    }
    
    public void setValueSource(String valueSource) {
        this.valueSource = valueSource;
    }
    
    public List<String> getEnumValues() {
        return enumValues;
    }
    
    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }
    
    public String getSuggestApi() {
        return suggestApi;
    }
    
    public void setSuggestApi(String suggestApi) {
        this.suggestApi = suggestApi;
    }
    
    public boolean isEncrypted() {
        return isEncrypted;
    }
    
    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }
}





