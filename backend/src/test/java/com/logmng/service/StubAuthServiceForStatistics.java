package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Test stub for AuthService when testing ActivityStatisticsController.
 * Returns getCurrentUserInfo with configurable scope for statistics (TC-13).
 */
public class StubAuthServiceForStatistics extends AuthService {

    private final String scope; // "self", "team", "all"

    public StubAuthServiceForStatistics(String scope) {
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
        resp.setScreenScopes(Map.of("statistics", scope));
        return resp;
    }
}
