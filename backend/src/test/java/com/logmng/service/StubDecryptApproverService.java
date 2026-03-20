package com.logmng.service;

import com.logmng.dto.response.UserListItemResponse;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

/**
 * Test stub for DecryptApproverService when Mockito cannot mock the class (e.g. Java 17+).
 * Used by SearchHistoryServiceTest and DecryptControllerTest.
 */
public class StubDecryptApproverService extends DecryptApproverService {

    public StubDecryptApproverService() {
        super(null, null);
    }

    @Override
    public boolean isAdmin(String role) {
        return false;
    }

    @Override
    public boolean isAdmin(boolean isSystemAdmin) {
        return isSystemAdmin;
    }

    @Override
    public boolean isApprover(Long appUserId) {
        return true;
    }

    @Override
    public boolean canApproveForRequester(Long approverUserId, Long requesterUserId) {
        return true;
    }

    @Override
    public List<UserListItemResponse> listUsers() {
        return Collections.emptyList();
    }
}
