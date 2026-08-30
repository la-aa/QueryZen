package com.queryzen.audit;

import com.queryzen.config.QueryZenProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 防篡改审计服务。
 *
 * 写入走 audit_writer（仅对审计表 INSERT），读取/校验走 audit_reader（仅 SELECT），
 * 遵循最小权限。哈希链由数据库触发器 TRG_AUDIT_CHAIN 维护：
 * 每行的 hash = sha256(上一行的 hash + content)，篡改任一历史行即断链。
 * 排障提示：若 verify 报「链被破坏」，说明审计表被外部改过，需从 AUDIT_HEAD 追溯。
 */
@Service
public class AuditService {

    private final HikariDataSource writerDs;
    private final HikariDataSource readerDs;
    private final String table;

    public AuditService(QueryZenProperties props) {
        QueryZenProperties.AuditProperties audit = props.getAudit();
        this.writerDs = build(audit.getWriter());
        this.readerDs = build(audit.getReader());
        this.table = audit.getSchema() + ".AUDIT_LOG";
    }

    private static HikariDataSource build(QueryZenProperties.DatasourceProps p) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getJdbcUrl());
        cfg.setUsername(p.getUsername());
        cfg.setPassword(p.getPassword());
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
            ps.setString(1, r.getUsername());
            ps.setString(2, r.getIp());
            ps.setString(3, r.getSqlText());
            ps.setInt(4, r.getRowsReturned());
            ps.setLong(5, r.getElapsedMs());
            ps.setString(6, r.getErrorMsg());
            ps.setString(7, content);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("审计写入失败: " + e.getMessage(), e);
        }
    }

    static String buildContent(AuditEntry r) {
        String sqlHash = sha256(r.getSqlText() == null ? "" : r.getSqlText());
        return r.getUsername() + "|" + r.getIp() + "|"
                + r.getRowsReturned() + "|" + r.getElapsedMs() + "|"
                + (r.getErrorMsg() == null ? "" : r.getErrorMsg()) + "|" + sqlHash;
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 小写十六进制（与原 JDK17 HexFormat.of().formatHex 输出一致）。 */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static class VerifyResult {
        private final boolean intact;
        private final long total;
        private final long checked;
        private final String message;

        public VerifyResult(boolean intact, long total, long checked, String message) {
            this.intact = intact;
            this.total = total;
            this.checked = checked;
            this.message = message;
        }

        public boolean isIntact() { return intact; }
        public long getTotal() { return total; }
        public long getChecked() { return checked; }
        public String getMessage() { return message; }
    }

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
        if (!"GENESIS".equalsIgnoreCase(rows.get(0).getPrevHash())) {
            return new VerifyResult(false, rows.size(), 0, "链首哈希异常（期望 GENESIS）");
        }

        long checked = 1;
        for (int i = 1; i < rows.size(); i++) {
            ChainRow prev = rows.get(i - 1);
            ChainRow cur = rows.get(i);
            String expected = sha256(prev.getHash() + cur.getContent());
            if (!expected.equalsIgnoreCase(cur.getHash()) || !prev.getHash().equalsIgnoreCase(cur.getPrevHash())) {
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

    private static class ChainRow {
        private final long seq;
        private final String prevHash;
        private final String hash;
        private final String content;

        ChainRow(long seq, String prevHash, String hash, String content) {
            this.seq = seq;
            this.prevHash = prevHash;
            this.hash = hash;
            this.content = content;
        }

        long getSeq() { return seq; }
        String getPrevHash() { return prevHash; }
        String getHash() { return hash; }
        String getContent() { return content; }
    }

    @PreDestroy
    void close() {
        writerDs.close();
        readerDs.close();
    }

    static class AuditEntry {
        private final String username;
        private final String ip;
        private final String sqlText;
        private final int rowsReturned;
        private final long elapsedMs;
        private final String errorMsg;

        AuditEntry(String username, String ip, String sqlText, int rowsReturned, long elapsedMs, String errorMsg) {
            this.username = username;
            this.ip = ip;
            this.sqlText = sqlText;
            this.rowsReturned = rowsReturned;
            this.elapsedMs = elapsedMs;
            this.errorMsg = errorMsg;
        }

        String getUsername() { return username; }
        String getIp() { return ip; }
        String getSqlText() { return sqlText; }
        int getRowsReturned() { return rowsReturned; }
        long getElapsedMs() { return elapsedMs; }
        String getErrorMsg() { return errorMsg; }
    }
}
