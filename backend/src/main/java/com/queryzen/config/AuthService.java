package com.queryzen.config;

import com.queryzen.config.UserRepository.StoredUser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证与会话管理。
 * - 用户来源：优先 `AUDIT_OWNER.USERS` 表（数据库账号），其次回退到配置文件 `queryzen.users`（兜底 admin）。
 * - 会话：内存 ConcurrentHashMap，有效期 8 小时；改动配置或重启即全部失效，无需手动清理。
 * - 密码有效期：30 天（PASSWORD_VALIDITY）。过期后登录返回 mustChangePassword=true，
 *   由 AuthInterceptor 限制只能调用「修改密码」接口。
 * 排障提示：改密后用户仍报过期，检查该用户在 USERS 表的 pwd_changed_at 字段。
 */
@Service
public class AuthService {

    public static final Duration PASSWORD_VALIDITY = Duration.ofDays(30);

    public record Session(String token, String username, List<String> roles,
                          Instant expiry, boolean mustChangePassword) {
        public boolean valid() {
            return Instant.now().isBefore(expiry);
        }
    }

    private static final long SESSION_TTL_SECONDS = 8 * 3600;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final QueryZenProperties props;
    private final UserRepository userRepository;

    public AuthService(QueryZenProperties props, UserRepository userRepository) {
        this.props = props;
        this.userRepository = userRepository;
    }

    public Session login(String username, String password) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("用户名或密码错误");
        String hash = sha256(password);

        Optional<StoredUser> dbUser = userRepository.findByUsername(username);
        if (dbUser.isPresent()) {
            if (!dbUser.get().passwordSha256().equalsIgnoreCase(hash)) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            return newSession(username, dbUser.get().roles(),
                    dbUser.get().pwdChangedAt().plus(PASSWORD_VALIDITY).isBefore(Instant.now()));
        }

        QueryZenProperties.UserAccount configUser = props.users().stream()
                .filter(u -> u.username().equals(username))
                .filter(u -> u.passwordSha256().equalsIgnoreCase(hash))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        return newSession(username, configUser.roles(), false);
    }

    private Session newSession(String username, List<String> roles, boolean mustChange) {
        Session s = new Session(
                UUID.randomUUID().toString(),
                username,
                roles,
                Instant.now().plusSeconds(SESSION_TTL_SECONDS),
                mustChange);
        sessions.put(s.token(), s);
        return s;
    }

    public Session validate(String token) {
        Session s = sessions.get(token);
        if (s == null || !s.valid()) throw new IllegalArgumentException("未登录或会话已过期");
        return s;
    }

    public Session changePassword(Session current, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) throw new IllegalArgumentException("新密码至少 6 位");
        if (oldPassword == null || oldPassword.isBlank()) throw new IllegalArgumentException("请输入原密码");

        StoredUser dbUser = userRepository.findByUsername(current.username())
                .orElseThrow(() -> new IllegalArgumentException("该账号未启用自助改密，请联系管理员"));
        if (!dbUser.passwordSha256().equalsIgnoreCase(sha256(oldPassword))) {
            throw new IllegalArgumentException("原密码错误");
        }
        userRepository.updatePassword(current.username(), sha256(newPassword), false);

        sessions.remove(current.token());
        Session fresh = new Session(
                UUID.randomUUID().toString(),
                current.username(),
                dbUser.roles(),
                Instant.now().plusSeconds(SESSION_TTL_SECONDS),
                false);
        sessions.put(fresh.token(), fresh);
        return fresh;
    }

    public void resetPassword(String operator, String username, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) throw new IllegalArgumentException("密码至少 6 位");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("用户名不能为空");

        Optional<StoredUser> dbUser = userRepository.findByUsername(username);
        Optional<QueryZenProperties.UserAccount> configUser = props.users().stream()
                .filter(u -> u.username().equalsIgnoreCase(username))
                .findFirst();

        if (dbUser.isEmpty() && configUser.isEmpty()) throw new IllegalArgumentException("用户不存在: " + username);

        if (dbUser.isEmpty()) {
            userRepository.insert(username, sha256(newPassword), configUser.get().roles(), operator);
        }
        userRepository.updatePassword(username, sha256(newPassword), true);
    }

    public UserAccountView createUser(String creator, String username, String password, List<String> roles) {
        if (username == null || username.length() < 3 || username.length() > 64 || !username.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("用户名需为 3-64 位字母/数字/下划线/连字符");
        }
        if (password == null || password.length() < 6) throw new IllegalArgumentException("密码至少 6 位");
        List<String> safeRoles = roles == null || roles.isEmpty() ? List.of("user") : roles.stream().distinct().toList();

        if (userRepository.findByUsername(username.toLowerCase()).isPresent()
                || props.users().stream().anyMatch(u -> u.username().equalsIgnoreCase(username))) {
            throw new IllegalArgumentException("用户已存在: " + username);
        }
        userRepository.insert(username, sha256(password), safeRoles, creator);
        return new UserAccountView(username, safeRoles, creator, null);
    }

    public List<UserAccountView> listUsers() {
        Map<String, UserAccountView> merged = new LinkedHashMap<>();
        for (StoredUser du : userRepository.list()) {
            merged.put(du.username(), new UserAccountView(du.username(), du.roles(), du.createdBy(), du.createdAt()));
        }
        for (QueryZenProperties.UserAccount cu : props.users()) {
            merged.putIfAbsent(cu.username(), new UserAccountView(cu.username(), cu.roles(), "CONFIG", null));
        }
        return new ArrayList<>(merged.values());
    }

    public record UserAccountView(String username, List<String> roles, String createdBy, String createdAt) {}

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}