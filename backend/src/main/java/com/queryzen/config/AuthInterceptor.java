package com.queryzen.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：对所有 /api/** 生效（登录接口除外）。
 * - 校验 Authorization: Bearer token，未通过抛出 UnauthorizedException(401)。
 * - 密码已过期的会话（mustChangePassword=true）只能访问「修改密码」接口，
 *   其它一律抛 PasswordExpiredException(403)。
 * 权限的进一步细分在具体 Controller 内做（如用户管理 requireAdmin）。
 */
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
        AuthService.Session session = authService.validate(header.substring(7));
        if (session.mustChangePassword() && !"/api/auth/change-password".equals(request.getRequestURI())) {
            throw new PasswordExpiredException();
        }
        request.setAttribute(ATTR_SESSION, session);
        return true;
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() {
            super("未认证或会话已过期");
        }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException() {
            super("无权限执行该操作（仅限 admin）");
        }
    }

    public static class PasswordExpiredException extends RuntimeException {
        public PasswordExpiredException() {
            super("密码已过期，请先更新密码");
        }
    }
}