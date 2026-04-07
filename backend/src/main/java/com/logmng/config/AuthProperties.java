package com.logmng.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.login.mode, auth.ad.*, provisioning defaults (spec: specs/external-identity-auth.spec.yaml).
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Login login = new Login();
    private Ad ad = new Ad();
    private Provisioning provisioning = new Provisioning();

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public Ad getAd() {
        return ad;
    }

    public void setAd(Ad ad) {
        this.ad = ad;
    }

    public Provisioning getProvisioning() {
        return provisioning;
    }

    public void setProvisioning(Provisioning provisioning) {
        this.provisioning = provisioning;
    }

    public static class Login {
        /** local | ad */
        private String mode = "local";
        private boolean allowLocalInProduction = false;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isAllowLocalInProduction() {
            return allowLocalInProduction;
        }

        public void setAllowLocalInProduction(boolean allowLocalInProduction) {
            this.allowLocalInProduction = allowLocalInProduction;
        }
    }

    public static class Ad {
        private String ldapUrl = "";
        private String managerDn = "";
        private String managerPassword = "";
        private String userSearchBase = "";
        /** LDAP filter; {0} = principal (Spring LDAP MessageFormat). */
        private String userSearchFilter = "(sAMAccountName={0})";
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;

        public String getLdapUrl() {
            return ldapUrl;
        }

        public void setLdapUrl(String ldapUrl) {
            this.ldapUrl = ldapUrl;
        }

        public String getManagerDn() {
            return managerDn;
        }

        public void setManagerDn(String managerDn) {
            this.managerDn = managerDn;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        public String getUserSearchBase() {
            return userSearchBase;
        }

        public void setUserSearchBase(String userSearchBase) {
            this.userSearchBase = userSearchBase;
        }

        public String getUserSearchFilter() {
            return userSearchFilter;
        }

        public void setUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
        }

        public Integer getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(Integer connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public Integer getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(Integer readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Provisioning {
        private String defaultSourceSystem = "HR_SAMPLE";

        public String getDefaultSourceSystem() {
            return defaultSourceSystem;
        }

        public void setDefaultSourceSystem(String defaultSourceSystem) {
            this.defaultSourceSystem = defaultSourceSystem;
        }
    }
}
