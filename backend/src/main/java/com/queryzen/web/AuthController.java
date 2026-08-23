package com.queryzen.web;

import com.queryzen.config.AuthInterceptor;
import com.queryzen.config.AuthService;
import com.queryzen.config.AuthService.Session;
import com.queryzen.config.AuthService.UserAccountView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 认证接口：登录/改密通用；用户管理（列表/创建/重置）仅 admin 角色可调，见 requireAdmin。
 * 登录失败、过期、越权分别返回 401/403，错误信息在 GlobalExceptionHandler 统一映射。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(String username, String password) {}

    public record CreateUserRequest(String username, String password, List<String> roles) {}

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    public record ResetPasswordRequest(String username, String newPassword) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        Session s = authService.login(req.username(), req.password());
        return Map.of(
                "token", s.token(),
                "username", s.username(),
                "roles", s.roles(),
                "passwordExpired", s.mustChangePassword());
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(HttpServletRequest request,
                                              @RequestBody ChangePasswordRequest req) {
        Session current = (Session) request.getAttribute(AuthInterceptor.ATTR_SESSION);
        Session fresh = authService.changePassword(current, req.oldPassword(), req.newPassword());
        return Map.of(
                "token", fresh.token(),
                "username", fresh.username(),
                "roles", fresh.roles(),
                "passwordExpired", fresh.mustChangePassword());
    }

    @GetMapping("/users")
    public List<UserAccountView> listUsers(HttpServletRequest request) {
        requireAdmin(request);
        return authService.listUsers();
    }

    @PostMapping("/users")
    public UserAccountView createUser(HttpServletRequest request,
                                      @RequestBody CreateUserRequest req) {
        Session creator = requireAdmin(request);
        return authService.createUser(
                creator.username(),
                req.username(),
                req.password(),
                Optional.ofNullable(req.roles()).orElse(List.of()));
    }

    @PostMapping("/users/reset-password")
    public Map<String, String> resetPassword(HttpServletRequest request,
                                             @RequestBody ResetPasswordRequest req) {
        Session operator = requireAdmin(request);
        authService.resetPassword(operator.username(), req.username(), req.newPassword());
        return Map.of(
                "result", "ok",
                "message", "已重置 " + req.username() + " 的密码，下次登录需更新密码");
    }

    private Session requireAdmin(HttpServletRequest request) {
        Session s = (Session) request.getAttribute(AuthInterceptor.ATTR_SESSION);
        if (s == null || !s.roles().contains("admin")) throw new AuthInterceptor.ForbiddenException();
        return s;
    }
}