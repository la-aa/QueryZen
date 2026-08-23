package com.queryzen.web;

import com.queryzen.config.AuthInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常映射为统一 JSON：{"error":"..."}
 * 401=未认证/会话过期，403=越权或密码过期，400=参数/业务错误（IllegalArgumentException），500=其它。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthInterceptor.UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> unauthorized(AuthInterceptor.UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AuthInterceptor.ForbiddenException.class)
    public ResponseEntity<Map<String, String>> forbidden(AuthInterceptor.ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AuthInterceptor.PasswordExpiredException.class)
    public ResponseEntity<Map<String, String>> passwordExpired(AuthInterceptor.PasswordExpiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internal(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}