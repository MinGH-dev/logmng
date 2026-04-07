package com.logmng.exception;

import com.logmng.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 커스텀 예외 처리
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(CustomException e) {
        log.error("커스텀 예외 발생: {}", e.getMessage(), e);
        
        ApiResponse<Object> response = ApiResponse.failure(
                e.getMessage(),
                e.getErrorCode()
        );
        if (e.getDetails() != null && !e.getDetails().isEmpty()) {
            response.setData(e.getDetails());
        }
        
        return ResponseEntity.status(e.getHttpStatus()).body(response);
    }
    
    /**
     * 유효성 검증 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        log.warn("유효성 검증 실패: {}", e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ApiResponse<Map<String, String>> response = ApiResponse.failure(
                "입력값 검증에 실패했습니다.",
                "VALIDATION_ERROR"
        );
        response.setData(errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 잘못된 입력값 (e.g. search-history requestedAtFrom/To format). 400 반환.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("입력값 검증 실패: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.failure(e.getMessage(), "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 요청 파라미터 타입 불일치 (e.g. empty string for int, non-numeric for Long).
     * 500 대신 400 반환하여 "서버 오류" 대신 클라이언트 오류로 처리.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("요청 파라미터 타입 불일치: name={}, value={}", e.getName(), e.getValue(), e);
        ApiResponse<Object> response = ApiResponse.failure(
                "요청 파라미터가 올바르지 않습니다. (" + (e.getName() != null ? e.getName() : "") + ")",
                "BAD_REQUEST"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("예상치 못한 예외 발생: type={}", e.getClass().getName(), e);

        ApiResponse<Object> response = ApiResponse.failure(
                "서버 오류가 발생했습니다.",
                "INTERNAL_SERVER_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Error 및 기타 Throwable (컨트롤러 밖 직렬화 실패 등). 로깅 후 500 반환.
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Object>> handleThrowable(Throwable t) {
        log.error("예상치 못한 Throwable: type={}, message={}", t.getClass().getName(), t.getMessage(), t);

        ApiResponse<Object> response = ApiResponse.failure(
                "서버 오류가 발생했습니다.",
                "INTERNAL_SERVER_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}





