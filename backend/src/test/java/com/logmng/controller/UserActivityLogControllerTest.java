package com.logmng.controller;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.service.StubAuthServiceForActivityLog;
import com.logmng.service.StubUserActivityLogServiceCapture;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new StubDataSource();
        stubService = new StubUserActivityLogServiceCapture(dataSource);
        configureController("self");
    }

    private void configureController(String scope) {
        StubAuthServiceForActivityLog stubAuth = new StubAuthServiceForActivityLog(scope);
        UserActivityLogController controller = new UserActivityLogController(stubService, stubAuth, dataSource, com.logmng.service.StubAppUserResolver.withOtherUser());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchActivityLogs_scopeSelf_ignoresUserIdAndDepartment_fixesToCurrentUser() throws Exception {
        mockMvc.perform(post("/api/activity-log/search")
                        .contentType("application/json")
                        .content("{\"userId\":20260002,\"department\":\"D01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserActivityLogSearchRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserIdForFilter()).isEqualTo("currentUser");
        assertThat(captured.getDepartment()).isNull();
    }

    @Test
    void searchActivityLogs_scopeSelf_ignoresUsernameDepartmentCodeIpAndClientAllowedUserIds() throws Exception {
        mockMvc.perform(post("/api/activity-log/search")
                        .contentType("application/json")
                        .content("{\"userId\":20260002,\"username\":\"Other User\",\"departmentCode\":\"전체\",\"ipAddress\":\"10.10.10.10\",\"allowedUserIds\":[\"otherUser\",\"thirdUser\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserActivityLogSearchRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserIdForFilter()).isEqualTo("currentUser");
        assertThat(captured.getUsername()).isNull();
        assertThat(captured.getDepartment()).isNull();
        assertThat(captured.getIpAddress()).isNull();
        assertThat(captured.getAllowedUserIds()).isNull();
    }

    @Test
    void searchActivityLogs_scopeAll_preservesLegitimateCrossUserFilters() throws Exception {
        configureController("all");

        mockMvc.perform(post("/api/activity-log/search")
                        .contentType("application/json")
                        .content("{\"userId\":20260002,\"username\":\"Other User\",\"departmentCode\":\"D01\",\"ipAddress\":\"10.10.10.10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserActivityLogSearchRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserIdForFilter()).isEqualTo("otherUser");
        assertThat(captured.getUsername()).isEqualTo("Other User");
        assertThat(captured.getDepartment()).isEqualTo("D01");
        assertThat(captured.getIpAddress()).isEqualTo("10.10.10.10");
        assertThat(captured.getAllowedUserIds()).isNull();
    }
}
