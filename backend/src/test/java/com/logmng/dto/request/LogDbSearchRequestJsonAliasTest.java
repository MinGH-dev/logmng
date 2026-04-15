package com.logmng.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDbSearchRequestJsonAliasTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_acceptsCamelCaseAliasesForDataAndHeaderString() throws Exception {
        String json = "{\"dataString\":\"LOCAL\",\"headerString\":\"HDR\"}";

        LogDbSearchRequest request = objectMapper.readValue(json, LogDbSearchRequest.class);

        assertThat(request.getDatastring()).isEqualTo("LOCAL");
        assertThat(request.getHeaderstring()).isEqualTo("HDR");
    }

    @Test
    void deserialize_acceptsPascalCaseAliasesForDataAndHeaderString() throws Exception {
        String json = "{\"DataString\":\"LOCAL2\",\"HeaderString\":\"HDR2\"}";

        LogDbSearchRequest request = objectMapper.readValue(json, LogDbSearchRequest.class);

        assertThat(request.getDatastring()).isEqualTo("LOCAL2");
        assertThat(request.getHeaderstring()).isEqualTo("HDR2");
    }
}
