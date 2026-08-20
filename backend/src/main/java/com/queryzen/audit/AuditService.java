package com.queryzen.audit;

import com.queryzen.config.QueryZenProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuditService {

    private final HikariDataSource writerDs;
    private final HikariDataSource readerDs;
    private final String table;

    public AuditService(QueryZenProperties props) {
        QueryZenProperties.AuditProperties audit = props.audit();
        this.writerDs = build(audit.writer());
        this.readerDs = build(audit.reader());
        this.table = audit.schema() + ".AUDIT_LOG";
    }

    private static HikariDataSource build(QueryZenProperties.DatasourceProps p) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.jdbcUrl());
        cfg.setUsername(p.username());
        cfg.setPassword(p.password());
        cfg.setDriverClassName("oracle.jdbc.OracleDriver");
        cfg.setMaximumPoolSize(2);
        return new HikariDataSource(cfg);
    }

    public void record(String username, String ip, String sql, int rows, long elapsedMs, String errorMsg) {
        AuditEntry r = new AuditEntry(username, ip, sql, rows, elapsedMs, errorMsg);
        String content = buildContent(r);
        String insert = "INSERT INTO " + table
                + " (USERNAME, IP, SQL_TEXT, ROWS_RETURNED, ELAPSED_MS, ERROR_MSG, CONTENT) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = writerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setString(1, r.username());
            ps.setString(2, r.ip());
            ps.setString(3, r.sqlText());
            ps.setInt(4, r.rowsReturned());
            ps.setLong(5, r.elapsedMs());
            ps.setString(6, r.errorMsg());
            ps.setString(7, content);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("审计写入失败: " + e.getMessage(), e);
        }
    }

    static String buildContent(AuditEntry r) {
        String sqlHash = sha256(r.sqlText() == null ? "" : r.sqlText());
        return r.username() + "|" + r.ip() + "|"
                + r.rowsReturned() + "|" + r.elapsedMs() + "|"
                + (r.errorMsg() == null ? "" : r.errorMsg()) + "|" + sqlHash;
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record VerifyResult(boolean intact, long total, long checked, String message) {}

    public VerifyResult verifyChain() {
        String sql = "SELECT SEQ, PREV_HASH, HASH, CONTENT FROM " + table + " ORDER BY SEQ";
        List<ChainRow> rows = new ArrayList<>();
        try (Connection c = readerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ChainRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("审计校验失败: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) return new VerifyResult(true, 0, 0, "审计日志为空");
        if (!"GENESIS".equalsIgnoreCase(rows.get(0).prevHash())) {
            return new VerifyResult(false, rows.size(), 0, "链首哈希异常（期望 GENESIS）");
        }

        long checked = 1;
        for (int i = 1; i < rows.size(); i++) {
            ChainRow prev = rows.get(i - 1);
            ChainRow cur = rows.get(i);
            String expected = sha256(prev.hash() + cur.content());
            if (!expected.equalsIgnoreCase(cur.hash()) || !prev.hash().equalsIgnoreCase(cur.prevHash())) {
                return new VerifyResult(false, rows.size(), i, "链完整性被破坏（第 " + (i + 1) + " 条）");
            }
            checked++;
        }
        return new VerifyResult(true, rows.size(), checked,
                "链完整，共 " + rows.size() + " 条记录全部校验通过");
    }

    public List<AuditRecord> recent(int limit) {
        String sql = "SELECT USERNAME, IP, SQL_TEXT, ROWS_RETURNED, ELAPSED_MS, ERROR_MSG, "
                + "TO_CHAR(TS,'YYYY-MM-DD HH24:MI:SS') "
                + "FROM " + table + " ORDER BY SEQ DESC FETCH FIRST " + limit + " ROWS ONLY";
        List<AuditRecord> out = new ArrayList<>();
        try (Connection c = readerDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new AuditRecord(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getLong(5),
                        rs.getString(6),
                        rs.getString(7)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("审计查询失败: " + e.getMessage(), e);
        }
        return out;
    }

    private record ChainRow(long seq, String prevHash, String hash, String content) {}

    @PreDestroy
    void close() {
        writerDs.close();
        readerDs.close();
    }

    record AuditEntry(String username, String ip, String sqlText, int rowsReturned, long elapsedMs, String errorMsg) {}
}