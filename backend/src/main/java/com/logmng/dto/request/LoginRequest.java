package com.logmng.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO.
 * Shape depends on {@code auth.login.mode}: {@code local} → exactly one of
 * {@code employeeNumber} or legacy numeric {@code userId}, plus {@code password};
 * {@code ad} → {@code principal} + {@code password}. Validated in {@link com.logmng.service.AuthService}.
 */
public class LoginRequest {

    /** app_user.employee_number — primary human-facing identifier for auth.login.mode=local */
    private String employeeNumber;

    /** app_user.id — deprecated legacy identifier for auth.login.mode=local */
    private Long userId;

    /** Directory login id (e.g. sAMAccountName) — used when auth.login.mode=ad */
    private String principal;

    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(Long userId, String password) {
        this.userId = userId;
        this.password = password;
    }

    public LoginRequest(String employeeNumber, String password) {
        this.employeeNumber = employeeNumber;
        this.password = password;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
