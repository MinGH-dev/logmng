package com.logmng.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbTestControllerMaskTest {

    @Test
    void maskPossibleUserInfo_stripsEmbeddedCredentials() {
        String raw = "jdbc:postgresql://dbuser:secret@db.example.com:5432/logmng";
        assertThat(DbTestController.maskPossibleUserInfo(raw))
                .isEqualTo("jdbc:postgresql://dbuser:***@db.example.com:5432/logmng");
    }

    @Test
    void maskPossibleUserInfo_leavesTypicalPostgresUrlUnchanged() {
        String u = "jdbc:postgresql://localhost:5432/logmng";
        assertThat(DbTestController.maskPossibleUserInfo(u)).isEqualTo(u);
    }
}
