package com.logmng.controller;

import com.logmng.service.StubActivityStatisticsServiceCapture;
import com.logmng.service.StubAuthServiceForStatistics;
import com.logmng.service.FilterOptionsService;
import com.logmng.service.DepartmentService;
import com.logmng.service.AppUserResolver;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-13: statistics list/export API with scope=self and userId, department, username
 * → response only current user's data; params ignored.
 */
class ActivityStatisticsControllerTest {

    private MockMvc mockMvc;
    private StubActivityStatisticsServiceCapture stubService;
    private StubAuthServiceForStatistics stubAuth;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new StubDataSource();
        stubService = new StubActivityStatisticsServiceCapture(dataSource);
        stubAuth = new StubAuthServiceForStatistics("self");
        FilterOptionsService filterOptionsService = new FilterOptionsService(new DepartmentService(dataSource));
        AppUserResolver stubResolver = new AppUserResolver(dataSource) {
            @Override
            public String getUsernameById(Long id) { return null; }
        };
        ActivityStatisticsController controller = new ActivityStatisticsController(
                stubService, stubAuth, filterOptionsService, dataSource, stubResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDaily_scopeSelf_ignoresUserIdDepartmentUsername_fixesToCurrentUser() throws Exception {
        mockMvc.perform(get("/api/statistics/activity/daily")
                        .param("userId", "20260002")
                        .param("department", "D01")
                        .param("username", "Someone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(stubService.getLastUserId()).isEqualTo("currentUser");
        assertThat(stubService.getLastAllowedUserIds()).isNull();
        assertThat(stubService.getLastDepartment()).isNull();
        assertThat(stubService.getLastIp()).isNull();
        assertThat(stubService.getLastUsername()).isNull();
    }
}
