package com.queryzen.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_SESSION = "queryzen.session";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new UnauthorizedException();
        request.setAttribute(ATTR_SESSION, authService.validate(header.substring(7)));
        return true;
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() {
            super("未认证或会话已过期");
        }
    }
}