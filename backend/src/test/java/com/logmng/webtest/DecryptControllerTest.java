package com.logmng.webtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.controller.DecryptController;
import com.logmng.service.DecryptApproverService;
import com.logmng.service.StubLogDbService;
import com.logmng.service.StubSearchHistoryService;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
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
        DecryptController controller = new DecryptController(logDbService, searchHistoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:decrypt_test;DB_CLOSE_DELAY=-1");
        return ds;
    }

    @Test
    void decryptRow_whenRowNotInApprovedSnapshot_returns403WithCode() throws Exception {
        searchHistoryService.setValidApprovalForUser(true);
        searchHistoryService.setRowInApprovedSnapshot(false);

        Map<String, String> body = Map.of(
                "searchHistoryId", "100",
                "guid", "guid-not-in-snapshot"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ROW_NOT_IN_APPROVED_SNAPSHOT"));
    }

    @Test
    void decryptRow_whenRowInApprovedSnapshot_proceedsToDecrypt() throws Exception {
        searchHistoryService.setValidApprovalForUser(true);
        searchHistoryService.setRowInApprovedSnapshot(true);
        logDbService.setDecryptRowResult(Map.of("decrypted", "data"));

        Map<String, String> body = Map.of(
                "searchHistoryId", "101",
                "guid", "guid-in-snapshot"
        );

        mockMvc.perform(post("/api/logs/decrypt/java_fw_imglog")
                        .sessionAttr("userId", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decrypted").value("data"));
    }
}
