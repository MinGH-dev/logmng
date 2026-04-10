package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Test stub for AuthService when testing UserActivityLogController.
 * Returns getCurrentUserInfo with configurable scope for activity-log (TC-12).
 */
public class StubAuthServiceForActivityLog extends AuthService {

    private final String scope; // "self", "team", "all"

    public StubAuthServiceForActivityLog(String scope) {
        super(null, null, null, null, new AuthProperties(), null, null);
        this.scope = scope != null ? scope : "team";
    }

    @Override
    public boolean checkAuth(HttpServletRequest request) {
        return true;
    }

    @Override
    public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
        LoginResponse resp = new LoginResponse();
        resp.setUsername("currentUser");
        resp.setIsSystemAdmin(false);
        resp.setScreenScopes(Map.of("activity-log", scope));
        return resp;
    }
}
