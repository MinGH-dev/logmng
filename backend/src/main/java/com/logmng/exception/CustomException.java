package com.logmng.exception;

import org.springframework.http.HttpStatus;

/**
 * 커스텀 예외 클래스
 */
public class CustomException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
    
    public CustomException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
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

