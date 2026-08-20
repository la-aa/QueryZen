package com.queryzen.config;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public record Session(String token, String username, List<String> roles, Instant expiry) {
        public boolean valid() {
            return Instant.now().isBefore(expiry);
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final QueryZenProperties props;

    public AuthService(QueryZenProperties props) {
        this.props = props;
    }

    public Session login(String username, String password) {
        return props.users().stream()
                .filter(u -> u.username().equals(username))
                .filter(u -> u.passwordSha256().equalsIgnoreCase(sha256(password)))
                .findFirst()
                .map(u -> {
                    Session s = new Session(
                            UUID.randomUUID().toString(),
                            u.username(),
                            u.roles(),
                            Instant.now().plusSeconds(8 * 3600));
                    sessions.put(s.token(), s);
                    return s;
                })
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
    }

    public Session validate(String token) {
        Session s = sessions.get(token);
        if (s == null || !s.valid()) throw new IllegalArgumentException("未登录或会话已过期");
        return s;
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}