package com.logmng.controller;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.service.StubAuthServiceForActivityLog;
import com.logmng.service.StubUserActivityLogServiceCapture;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-12: activity-log list API with scope=self and params userId, department
 * → response only current user's data; params ignored.
 */
class UserActivityLogControllerTest {

    private MockMvc mockMvc;
    private StubUserActivityLogServiceCapture stubService;
    private StubAuthServiceForActivityLog stubAuth;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new StubDataSource();
        stubService = new StubUserActivityLogServiceCapture(dataSource);
        stubAuth = new StubAuthServiceForActivityLog("self");
        UserActivityLogController controller = new UserActivityLogController(stubService, stubAuth, dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchActivityLogs_scopeSelf_ignoresUserIdAndDepartment_fixesToCurrentUser() throws Exception {
        mockMvc.perform(post("/api/activity-log/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"otherUser\",\"department\":\"D01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserActivityLogSearchRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isEqualTo("currentUser");
        assertThat(captured.getDepartment()).isNull();
    }
}
