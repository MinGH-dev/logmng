package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single sort column for multi-column ORDER BY (PB FEP log search).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogDbSortSpec {

    /** DB column name (allowlisted in LogDbService). */
    @JsonProperty("field")
    private String field;

    /** "asc" or "desc". */
    @JsonProperty("direction")
    private String direction;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
