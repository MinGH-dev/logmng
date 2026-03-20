package com.logmng.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 로그인 요청 DTO.
 * 계약: 요청 body는 userId (number, app_user.id)와 password만 사용. username은 로그인에 사용하지 않음.
 */
public class LoginRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(Long userId, String password) {
        this.userId = userId;
        this.password = password;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
