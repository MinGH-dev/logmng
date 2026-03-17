package com.logmng.webtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.controller.DecryptController;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.StubLogDbService;
import com.logmng.service.StubSearchHistoryService;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DecryptController tests (decryption approval snapshot).
 * Uses standalone MockMvc with stub services to avoid Mockito/Spring context issues on Java 17+.
 * Ref: docs/requirements/20260224-decryption-snapshot-final-design-en.md §6.1, §6.4
 */
class DecryptControllerTest {

    private MockMvc mockMvc;
    private StubSearchHistoryService searchHistoryService;
    private StubLogDbService logDbService;
    private StubAuthServiceDecryptAllowed authServiceStub;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = createH2DataSource();
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        logDbService = new StubLogDbService(dataSource, cryptoUtil);
        DecryptApproverService decryptApproverService = new com.logmng.service.StubDecryptApproverService();
        searchHistoryService = new StubSearchHistoryService(dataSource, logDbService, decryptApproverService);
        authServiceStub = new StubAuthServiceDecryptAllowed();
        authServiceStub.setCurrentUserId(20260001L);
        authServiceStub.setCurrentUsername("user1");
        DecryptController controller = new DecryptController(logDbService, searchHistoryService, authServiceStub);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:decrypt_test;DB_CLOSE_DELAY=-1");
        return ds;
    }

    @Test
    @DisplayName("TC-02: approver executes decrypt → 403 DECRYPTION_NOT_APPROVED")
    void decryptRow_whenValidApprovalFalse_returns403DecryptionNotApproved() throws Exception {
        searchHistoryService.setValidApprovalForUser(false);
        searchHistoryService.setRowInApprovedSnapshot(true);

        Map<String, String> body = Map.of(
                "searchHistoryId", "100",
                "guid", "guid-any"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DECRYPTION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("TC-05: guid not in search_history_approved_row → 403 ROW_NOT_IN_APPROVED_SNAPSHOT")
    void decryptRow_whenRowNotInApprovedSnapshot_returns403WithCode() throws Exception {
        searchHistoryService.setValidApprovalForUser(true);
        searchHistoryService.setRowInApprovedSnapshot(false);

        Map<String, String> body = Map.of(
                "searchHistoryId", "100",
                "guid", "guid-not-in-snapshot"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ROW_NOT_IN_APPROVED_SNAPSHOT"));
    }

    @Test
    @DisplayName("TC-01: requester executes decrypt with valid approval and row in snapshot → 200")
    void decryptRow_requesterExecutesWithValidApproval_returns200() throws Exception {
        searchHistoryService.setValidApprovalForUser(true);
        searchHistoryService.setRowInApprovedSnapshot(true);
        logDbService.setDecryptRowResult(Map.of("decrypted", "data"));

        Map<String, String> body = Map.of(
                "searchHistoryId", "101",
                "guid", "guid-in-snapshot"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decrypted").value("data"));
    }

    @Test
    @DisplayName("TC-04: search_history.user_id wrong type or value → 403 DECRYPTION_NOT_APPROVED, no 500")
    void decryptRow_whenUserIdMismatchOrWrongType_returns403DecryptionNotApproved() throws Exception {
        authServiceStub.setCurrentUserId(20260002L);
        authServiceStub.setCurrentUsername("user2");
        searchHistoryService.setValidApprovalForUser(false);
        searchHistoryService.setRowInApprovedSnapshot(true);

        Map<String, String> body = Map.of(
                "searchHistoryId", "100",
                "guid", "guid-any"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DECRYPTION_NOT_APPROVED"));
    }

    /** Stub AuthService that allows decrypt (hasDecryptForMain returns true). Configurable current user for TC-01/TC-02/TC-04. */
    private static class StubAuthServiceDecryptAllowed extends AuthService {
        private long currentUserId = 20260001L;
        private String currentUsername = "user1";

        StubAuthServiceDecryptAllowed() {
            super(null, null, null, null, null);
        }

        void setCurrentUserId(long userId) {
            this.currentUserId = userId;
        }

        void setCurrentUsername(String username) {
            this.currentUsername = username;
        }

        @Override
        public boolean hasDecryptForMain(HttpServletRequest request) {
            return true;
        }

        @Override
        public com.logmng.dto.response.LoginResponse getCurrentUserInfo(HttpServletRequest request) {
            com.logmng.dto.response.LoginResponse r = new com.logmng.dto.response.LoginResponse();
            r.setUsername(currentUsername);
            r.setUserId(currentUserId);
            r.setSelfContext(new com.logmng.dto.response.LoginResponse.SelfContext(null, currentUsername, currentUserId));
            return r;
        }

        @Override
        public boolean checkAuth(HttpServletRequest request) {
            return true;
        }
    }
}
