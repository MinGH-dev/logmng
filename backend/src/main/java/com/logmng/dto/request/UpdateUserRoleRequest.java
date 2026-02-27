package com.logmng.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 사용자 역할 변경 요청 본문 (§7.4).
 * role: ADMIN | USER
 */
public class UpdateUserRoleRequest {

    @NotBlank(message = "role is required")
    @Pattern(regexp = "^(ADMIN|USER)$", message = "role must be ADMIN or USER")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
