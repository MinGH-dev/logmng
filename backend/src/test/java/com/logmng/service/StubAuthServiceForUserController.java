package com.logmng.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test stub for AuthService when testing UserController.
 * Configurable checkAuth and canAccessUserManagementView (Mockito cannot mock AuthService on Java 25+).
 */
public class StubAuthServiceForUserController extends AuthService {

    private final AtomicBoolean checkAuthResult = new AtomicBoolean(true);
    private final AtomicBoolean canAccessUserManagementViewResult = new AtomicBoolean(true);

    public StubAuthServiceForUserController() {
        super(null, null, null, null, null);
    }

    public void setCheckAuth(boolean value) {
        checkAuthResult.set(value);
    }

    public void setCanAccessUserManagementView(boolean value) {
        canAccessUserManagementViewResult.set(value);
    }

    @Override
    public boolean checkAuth(HttpServletRequest request) {
        return checkAuthResult.get();
    }

    @Override
    public boolean canAccessUserManagementView(HttpServletRequest request) {
        return canAccessUserManagementViewResult.get();
    }
}
