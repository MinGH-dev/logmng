package com.logmng.controller;

import com.logmng.service.StubActivityStatisticsServiceCapture;
import com.logmng.service.StubAuthServiceForStatistics;
import com.logmng.service.FilterOptionsService;
import com.logmng.service.DepartmentService;
import com.logmng.service.AppUserResolver;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-13: statistics list/export API with scope=self and userId, department, username
 * → response only current user's data; params ignored.
 * TC-01–TC-05 (req 20260317): scope=team statistics return 200, no exception.
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

    @Nested
    class ScopeTeam {
        private static DataSource scopeTeamH2;

        @BeforeEach
        void initScopeTeamH2() throws Exception {
            if (scopeTeamH2 == null) {
                scopeTeamH2 = createH2ForScopeTeam();
            }
        }

        /** TC-01: scope=team GET /api/statistics/users → 200, no exception. */
        @Test
        void getUsers_scopeTeam_returns200() throws Exception {
            MockMvc mvc = mockMvcForScopeTeam(scopeTeamH2);
            mvc.perform(get("/api/statistics/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }

        /** TC-02: scope=team GET /api/statistics/ips → 200, no exception. */
        @Test
        void getIps_scopeTeam_returns200() throws Exception {
            MockMvc mvc = mockMvcForScopeTeam(scopeTeamH2);
            mvc.perform(get("/api/statistics/ips"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }

        /** TC-03: scope=team GET /api/statistics/activity/daily → 200, no exception. */
        @Test
        void getDaily_scopeTeam_returns200() throws Exception {
            MockMvc mvc = mockMvcForScopeTeam(scopeTeamH2);
            mvc.perform(get("/api/statistics/activity/daily"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        /** TC-04: scope=team GET /api/statistics/activity/monthly → 200, no exception. */
        @Test
        void getMonthly_scopeTeam_returns200() throws Exception {
            MockMvc mvc = mockMvcForScopeTeam(scopeTeamH2);
            mvc.perform(get("/api/statistics/activity/monthly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        private static MockMvc mockMvcForScopeTeam(DataSource h2) {
            StubAuthServiceForStatistics teamAuth = new StubAuthServiceForStatistics("team");
            StubActivityStatisticsServiceCapture teamStub = new StubActivityStatisticsServiceCapture(h2);
            FilterOptionsService filterOptionsService = new FilterOptionsService(new DepartmentService(h2));
            AppUserResolver resolver = new AppUserResolver(h2) { @Override public String getUsernameById(Long id) { return null; } };
            ActivityStatisticsController ctrl = new ActivityStatisticsController(
                    teamStub, teamAuth, filterOptionsService, h2, resolver);
            return MockMvcBuilders.standaloneSetup(ctrl)
                    .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                    .build();
        }

        private static DataSource createH2ForScopeTeam() throws Exception {
            Class.forName("org.h2.Driver");
            String url = "jdbc:h2:mem:stat_ctrl_team;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            try (Connection conn = java.sql.DriverManager.getConnection(url);
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS app_user (id BIGSERIAL PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, department_code VARCHAR(50), name VARCHAR(200))");
                st.execute("CREATE TABLE IF NOT EXISTS user_activity_log (id BIGSERIAL PRIMARY KEY, user_id VARCHAR(100), username VARCHAR(100), action_type VARCHAR(50), action_detail TEXT, ip_address VARCHAR(45), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                st.execute("MERGE INTO app_user (id, username, department_code, name) KEY(username) VALUES (1, 'currentUser', 'D1', 'Current User')");
            }
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL(url);
            return ds;
        }
    }
}
