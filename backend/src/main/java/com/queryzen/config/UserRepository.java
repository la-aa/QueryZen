package com.queryzen.config;

import com.queryzen.config.QueryZenProperties.DatasourceProps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 应用账号的数据库读写（AUDIT_OWNER.USERS）。
 * 读写分离沿用最小权限：INSERT/UPDATE 走 audit_writer，SELECT 走 audit_reader。
 * 注意：Oracle 的 UPDATE 隐式需要目标表 SELECT 权限（WRITER 需同时授予）。
 */
@Repository
public class UserRepository {

    private final HikariDataSource writerDs;
    private final HikariDataSource readerDs;
    private final String table;

    public UserRepository(QueryZenProperties props) {
        QueryZenProperties.AuditProperties audit = props.audit();
        this.writerDs = build(audit.writer());
        this.readerDs = build(audit.reader());
        this.table = audit.schema() + ".USERS";
    }

    private static HikariDataSource build(DatasourceProps p) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.jdbcUrl());
        cfg.setUsername(p.username());
        cfg.setPassword(p.password());
        cfg.setDriverClassName("oracle.jdbc.OracleDriver");
        cfg.setMaximumPoolSize(2);
        return new HikariDataSource(cfg);
    }

    public record StoredUser(String username, String passwordSha256, List<String> roles,
                             String createdBy, String createdAt, Instant pwdChangedAt) {}

    public Optional<StoredUser> findByUsername(String username) {
        String sql = "SELECT USERNAME, PASSWORD_SHA256, ROLES, CREATED_BY, "
                + "TO_CHAR(CREATED_AT,'YYYY-MM-DD HH24:MI:SS'), PWD_CHANGED_AT FROM " + table
                + " WHERE USERNAME = ?";
        try (Connection c = readerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException("用户查询失败: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<StoredUser> list() {
        String sql = "SELECT USERNAME, PASSWORD_SHA256, ROLES, CREATED_BY, "
                + "TO_CHAR(CREATED_AT,'YYYY-MM-DD HH24:MI:SS'), PWD_CHANGED_AT FROM " + table
                + " ORDER BY USERNAME";
        List<StoredUser> out = new ArrayList<>();
        try (Connection c = readerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) {
            throw new IllegalStateException("用户查询失败: " + e.getMessage(), e);
        }
        return out;
    }

    public void insert(String username, String passwordSha256, List<String> roles, String createdBy) {
        String sql = "INSERT INTO " + table
                + " (USERNAME, PASSWORD_SHA256, ROLES, CREATED_BY) VALUES (?,?,?,?)";
        try (Connection c = writerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordSha256);
            ps.setString(3, String.join(",", roles));
            ps.setString(4, createdBy);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("用户创建失败: " + e.getMessage(), e);
        }
    }

    public int updatePassword(String username, String passwordSha256, boolean resetExpire) {
        String sql = "UPDATE " + table
                + " SET PASSWORD_SHA256 = ?, PWD_CHANGED_AT = "
                + (resetExpire ? "SYSTIMESTAMP - INTERVAL '31' DAY" : "SYSTIMESTAMP")
                + " WHERE USERNAME = ?";
        try (Connection c = writerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordSha256);
            ps.setString(2, username);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("密码更新失败: " + e.getMessage(), e);
        }
    }

    private static StoredUser map(ResultSet rs) throws Exception {
        String rawRoles = rs.getString(3);
        Instant changedAt = rs.getTimestamp(6) == null ? Instant.EPOCH : rs.getTimestamp(6).toInstant();
        return new StoredUser(
                rs.getString(1),
                rs.getString(2),
                rawRoles == null || rawRoles.isBlank()
                        ? List.of()
                        : List.of(rawRoles.split(",")),
                rs.getString(4),
                rs.getString(5),
                changedAt);
    }

    @PreDestroy
    void close() {
        writerDs.close();
        readerDs.close();
    }
}