package com.ktb.community.global.exception;

import com.ktb.community.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationInputException() {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ApiResponse<>(ErrorCode.INVALID_INPUT.getMessage(), null));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ApiResponse<>(e.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException() {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ApiResponse<>(ErrorCode.INVALID_INPUT.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException() {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ApiResponse<>(ErrorCode.INVALID_INPUT.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), null));
    }
}
