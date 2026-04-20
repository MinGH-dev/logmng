package com.logmng.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures Spring Boot's primary {@link ObjectMapper} does not rename or drop {@code Map} keys on wireframe rows
 * (snake_case {@code keyword_match_*} must survive JSON encoding).
 */
@SpringBootTest
@ActiveProfiles("test")
class PbFepWireframeApiResponseJacksonIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiResponse_logDbSearchResponse_serializesMapRowKeys_withUnderscores() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("keyword_match_request_data", false);
        row.put("keyword_match_response_data", true);
        row.put("keyword_match_bmsg", false);
        row.put("keyword_match_data", true);
        LogDbSearchResponse body = new LogDbSearchResponse(
                List.of(row),
                new LogDbSearchResponse.PaginationInfo(1, 1, 1L));
        ApiResponse<LogDbSearchResponse> api = ApiResponse.success(body);

        String json = objectMapper.writeValueAsString(api);

        assertThat(json).contains("\"keyword_match_request_data\":false");
        assertThat(json).contains("\"keyword_match_response_data\":true");
        assertThat(json).contains("\"keyword_match_bmsg\":false");
        assertThat(json).contains("\"keyword_match_data\":true");
    }
}
