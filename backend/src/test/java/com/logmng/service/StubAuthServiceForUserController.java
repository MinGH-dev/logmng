package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.dto.response.LoginResponse;
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
        super(null, null, null, null, new AuthProperties(), null, null);
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

    @Override
    public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
        if (!checkAuthResult.get()) {
            return null;
        }
        LoginResponse r = new LoginResponse();
        if (request != null && request.getSession(false) != null) {
            Object username = request.getSession().getAttribute("username");
            if (username != null && !username.toString().isBlank()) {
                r.setUsername(username.toString().trim());
                return r;
            }
        }
        r.setUsername("stub-user");
        return r;
    }
}
