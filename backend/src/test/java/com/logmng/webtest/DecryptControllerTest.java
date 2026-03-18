package com.logmng.webtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.controller.DecryptController;
import com.logmng.service.AuthService;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.StubDecryptionAllowedService;
import com.logmng.service.StubLogDbService;
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
 * DecryptController tests. Authorization from decryption-allowed store only (req 20260318).
 * Uses standalone MockMvc with stub DecryptionAllowedService.
 */
class DecryptControllerTest {

    private MockMvc mockMvc;
    private StubDecryptionAllowedService decryptionAllowedService;
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
        decryptionAllowedService = new StubDecryptionAllowedService();
        authServiceStub = new StubAuthServiceDecryptAllowed();
        authServiceStub.setCurrentUserId(20260001L);
        authServiceStub.setCurrentUsername("user1");
        DecryptController controller = new DecryptController(logDbService, decryptionAllowedService, authServiceStub);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:decrypt_test;DB_CLOSE_DELAY=-1");
        return ds;
    }

    @Test
    @DisplayName("TC-06: guid not in decryption-allowed store → 403 DECRYPTION_NOT_APPROVED")
    void decryptRow_whenNotInDecryptionAllowed_returns403DecryptionNotApproved() throws Exception {
        decryptionAllowedService.setAllowed(false);

        Map<String, String> body = Map.of("guid", "guid-any");

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DECRYPTION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("TC-05: guid in decryption-allowed store and valid_until future → 200")
    void decryptRow_whenInDecryptionAllowed_returns200() throws Exception {
        decryptionAllowedService.setAllowed(true);
        logDbService.setDecryptRowResult(Map.of("decrypted", "data"));

        Map<String, String> body = Map.of("guid", "guid-in-snapshot");

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decrypted").value("data"));
    }

    @Test
    @DisplayName("TC-01: requester executes decrypt with guid in allowed set → 200")
    void decryptRow_requesterWithAllowedGuid_returns200() throws Exception {
        decryptionAllowedService.setAllowed(true);
        logDbService.setDecryptRowResult(Map.of("decrypted", "data"));

        Map<String, String> body = Map.of("guid", "guid-in-snapshot");

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", 20260001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decrypted").value("data"));
    }

    @Test
    @DisplayName("TC-04: different user / guid not allowed → 403 DECRYPTION_NOT_APPROVED")
    void decryptRow_whenGuidNotAllowed_returns403DecryptionNotApproved() throws Exception {
        authServiceStub.setCurrentUserId(20260002L);
        authServiceStub.setCurrentUsername("user2");
        decryptionAllowedService.setAllowed(false);

        Map<String, String> body = Map.of("guid", "guid-any");

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
