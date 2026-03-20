package com.logmng.service;

import com.logmng.dto.response.UserListItemResponse;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test stub for DecryptApproverService when testing UserController PUT (role update).
 * Configurable updateUserRole behavior to avoid Mockito on Java 25+.
 */
public class StubDecryptApproverServiceForRoleUpdate extends DecryptApproverService {

    private final AtomicReference<UserListItemResponse> updateResult = new AtomicReference<>();
    private final AtomicReference<RuntimeException> updateException = new AtomicReference<>();
    private boolean admin = true;

    public StubDecryptApproverServiceForRoleUpdate() {
        super(null, null);
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public void setUpdateResult(UserListItemResponse result) {
        updateResult.set(result);
        updateException.set(null);
    }

    public void setUpdateException(RuntimeException ex) {
        updateException.set(ex);
        updateResult.set(null);
    }

    @Override
    public boolean isAdmin(String role) {
        return admin;
    }

    @Override
    public boolean isAdmin(boolean isSystemAdmin) {
        return admin;
    }

    @Override
    public List<UserListItemResponse> listUsers() {
        return Collections.emptyList();
    }

    @Override
    public UserListItemResponse updateUserRole(String callerUserId, String targetUserId, String role) {
        RuntimeException ex = updateException.get();
        if (ex != null) {
            throw ex;
        }
        UserListItemResponse result = updateResult.get();
        if (result != null) {
            return result;
        }
        return new UserListItemResponse(20260001L, targetUserId, role, null, false, null, null, false);
    }
}
