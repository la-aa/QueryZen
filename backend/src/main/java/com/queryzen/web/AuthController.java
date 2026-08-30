package com.queryzen.web;

import com.queryzen.config.AuthInterceptor;
import com.queryzen.config.AuthService;
import com.queryzen.config.AuthService.Session;
import com.queryzen.config.AuthService.UserAccountView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class CreateUserRequest {
        private String username;
        private String password;
        private List<String> roles;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class ResetPasswordRequest {
        private String username;
        private String newPassword;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        Session s = authService.login(req.getUsername(), req.getPassword());
        return sessionMap(s);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(HttpServletRequest request,
                                              @RequestBody ChangePasswordRequest req) {
        Session current = (Session) request.getAttribute(AuthInterceptor.ATTR_SESSION);
        Session fresh = authService.changePassword(current, req.getOldPassword(), req.getNewPassword());
        return sessionMap(fresh);
    }

    private static Map<String, Object> sessionMap(Session s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", s.getToken());
        m.put("username", s.getUsername());
        m.put("roles", s.getRoles());
        m.put("passwordExpired", s.isMustChangePassword());
        return m;
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
                creator.getUsername(),
                req.getUsername(),
                req.getPassword(),
                Optional.ofNullable(req.getRoles()).orElse(Collections.<String>emptyList()));
    }

    @PostMapping("/users/reset-password")
    public Map<String, String> resetPassword(HttpServletRequest request,
                                             @RequestBody ResetPasswordRequest req) {
        Session operator = requireAdmin(request);
        authService.resetPassword(operator.getUsername(), req.getUsername(), req.getNewPassword());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("result", "ok");
        m.put("message", "已重置 " + req.getUsername() + " 的密码，下次登录需更新密码");
        return m;
    }

    private Session requireAdmin(HttpServletRequest request) {
        Session s = (Session) request.getAttribute(AuthInterceptor.ATTR_SESSION);
        if (s == null || !s.getRoles().contains("admin")) throw new AuthInterceptor.ForbiddenException();
        return s;
    }
}
