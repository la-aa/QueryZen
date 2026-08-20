package com.queryzen.web;

import com.queryzen.config.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        AuthService.Session s = authService.login(req.username(), req.password());
        return Map.of(
                "token", s.token(),
                "username", s.username(),
                "roles", s.roles());
    }
}