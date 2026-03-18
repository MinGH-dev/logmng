package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 API 응답 클래스
 * @param <T> 응답 데이터 타입
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private Boolean success;
    private T data;
    private String message;
    private String error;
    private String code;
    /** Optional subcode for client (e.g. EXECUTOR_NOT_REQUESTER for 403 DECRYPTION_NOT_APPROVED). */
    private String detailCode;

    public ApiResponse() {
    }

    public ApiResponse(Boolean success, T data, String message, String error, String code) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.code = code;
    }
    
    // Getters and Setters
    public Boolean getSuccess() {
        return success;
    }
    
    public void setSuccess(Boolean success) {
        this.success = success;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }

    public String getDetailCode() {
        return detailCode;
    }

    public void setDetailCode(String detailCode) {
        this.detailCode = detailCode;
    }

    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }
    
    /**
     * 성공 응답 생성 (메시지 포함)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage(message);
        return response;
    }
    
    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> failure(String error, String code) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(error);
        response.setCode(code);
        return response;
    }

    /**
     * 실패 응답 생성 (상세 코드 포함, e.g. EXECUTOR_NOT_REQUESTER)
     */
    public static <T> ApiResponse<T> failure(String error, String code, String detailCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(error);
        response.setCode(code);
        response.setDetailCode(detailCode);
        return response;
    }
    
    /**
     * 실패 응답 생성 (코드 없음)
     */
    public static <T> ApiResponse<T> failure(String error) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}

