package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Provisioning success: canonical {@code userId} is numeric {@code app_user.id}; {@code employeeNumber} mirrors HR replica when stored. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProvisionUserResultResponse {

    private long userId;
    private String username;
    private String employeeNumber;

    public ProvisionUserResultResponse() {
    }

    public ProvisionUserResultResponse(long userId, String username) {
        this(userId, username, null);
    }

    public ProvisionUserResultResponse(long userId, String username, String employeeNumber) {
        this.userId = userId;
        this.username = username;
        this.employeeNumber = employeeNumber;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }
}
