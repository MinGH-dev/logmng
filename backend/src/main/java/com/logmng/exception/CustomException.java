package com.logmng.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 커스텀 예외 클래스
 */
public class CustomException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;
    /** Optional payload for {@link com.logmng.dto.response.ApiResponse#setData(Object)} on error responses. */
    private final Map<String, Object> details;

    public String getErrorCode() {
        return errorCode;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Extra fields for the API error body (e.g. 409 conflict hints). May be null.
     */
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public CustomException(String message, String errorCode, HttpStatus httpStatus) {
        this(message, errorCode, httpStatus, null);
    }

    public CustomException(String message, String errorCode, HttpStatus httpStatus, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }
    
    /**
     * 400 Bad Request
     */
    public static CustomException badRequest(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 401 Unauthorized
     */
    public static CustomException unauthorized(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * 403 Forbidden
     */
    public static CustomException forbidden(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 404 Not Found
     */
    public static CustomException notFound(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.NOT_FOUND);
    }

    /**
     * 409 Conflict
     */
    public static CustomException conflict(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.CONFLICT);
    }

    /**
     * 409 Conflict with optional {@link #getDetails()} merged into ApiResponse {@code data}.
     */
    public static CustomException conflict(String message, String errorCode, Map<String, Object> details) {
        return new CustomException(message, errorCode, HttpStatus.CONFLICT, details);
    }

    /**
     * 503 Service Unavailable
     */
    public static CustomException serviceUnavailable(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * 500 Internal Server Error
     */
    public static CustomException internalError(String message, String errorCode) {
        return new CustomException(message, errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

