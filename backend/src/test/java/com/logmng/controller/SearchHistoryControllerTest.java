package com.logmng.controller;

import com.logmng.dto.request.SearchHistoryListRequest;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.service.AppUserResolver;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.SearchHistoryService;
import com.logmng.service.StubAppUserResolver;
import com.logmng.service.StubDecryptApproverService;
import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchHistoryControllerTest {

    private MockMvc mockMvc;
    private CapturingSearchHistoryService stubService;

    @BeforeEach
    void setUp() {
        stubService = new CapturingSearchHistoryService();
        DecryptApproverService decryptApproverService = new StubDecryptApproverService();
        AuthService authService = new NoopAuthService();
        StubAppUserResolver resolver = new StubAppUserResolver(new StubDataSource());
        resolver.map(20260001L, "requester-1");
        SearchHistoryController controller = new SearchHistoryController(
                stubService,
                decryptApproverService,
                authService,
                new StubDataSource(),
                resolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_scopeSelf_ignoresRequesterFiltersAndFixesCurrentUser() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("department", "D01")
                        .param("username", "other name")
                        .param("userId", "20260002")
                        .param("page", "3")
                        .param("pageSize", "10")
                        .param("sortDirection", "asc")
                        .sessionAttr("userId", "currentUser")
                        .sessionAttr("screenScopes", Map.of("search-history", "self")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getActorUserId()).isEqualTo("currentUser");
        assertThat(captured.getUserId()).isEqualTo("currentUser");
        assertThat(captured.getDepartment()).isNull();
        assertThat(captured.getUsername()).isNull();
        assertThat(captured.getAllowedUserIds()).isNull();
        assertThat(captured.getPage()).isEqualTo(3);
        assertThat(captured.getPageSize()).isEqualTo(10);
        assertThat(captured.getSortField()).isEqualTo("requested_at");
        assertThat(captured.getSortDirection()).isEqualTo("asc");
    }

    @Test
    void list_scopeAll_forwardsNormalizedRequesterFiltersAndDefaults() throws Exception {
        mockMvc.perform(get("/api/search-history")
                        .param("department", " D01 ")
                        .param("username", " alice ")
                        .param("userId", "20260001")
                        .sessionAttr("userId", "currentUser")
                        .sessionAttr("screenScopes", Map.of("search-history", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SearchHistoryListRequest captured = stubService.getLastRequest();
        assertThat(captured).isNotNull();
        assertThat(captured.getActorUserId()).isEqualTo("currentUser");
        assertThat(captured.getDepartment()).isEqualTo("D01");
        assertThat(captured.getUsername()).isEqualTo("alice");
        assertThat(captured.getUserId()).isEqualTo("requester-1");
        assertThat(captured.getAllowedUserIds()).isNull();
        assertThat(captured.getPage()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSortField()).isEqualTo("requested_at");
        assertThat(captured.getSortDirection()).isEqualTo("desc");
    }

    private static final class CapturingSearchHistoryService extends SearchHistoryService {

        private SearchHistoryListRequest lastRequest;

        private CapturingSearchHistoryService() {
            super(new StubDataSource(), null, new StubDecryptApproverService());
        }

        @Override
        public SearchHistoryListResponse list(SearchHistoryListRequest request) {
            this.lastRequest = request;
            return new SearchHistoryListResponse(
                    Collections.emptyList(),
                    new UserActivityLogResponse.PaginationInfo(1, 0, 0L));
        }

        private SearchHistoryListRequest getLastRequest() {
            return lastRequest;
        }
    }

    private static final class NoopAuthService extends AuthService {

        private NoopAuthService() {
            super(null, null, null, null);
        }
    }
}
