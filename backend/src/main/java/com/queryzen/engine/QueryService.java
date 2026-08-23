package com.queryzen.engine;

import com.queryzen.audit.AuditService;
import com.queryzen.config.DataSourceRegistry;
import com.queryzen.config.QueryZenProperties;
import com.queryzen.dialect.Dialect;
import com.queryzen.dialect.SqlGuard;
import org.springframework.stereotype.Service;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询执行入口。流程：取连接定义 → 方言校验(只读) → 行数改写 → 执行并读列/行 → 写审计。
 * 成功与失败都会写入审计（失败记录为 0 行 + 错误信息），便于监管追溯。
 */
@Service
public class QueryService {

    private final DataSourceRegistry registry;
    private final AuditService auditService;

    public QueryService(DataSourceRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    public QueryResult execute(String connectionId, String sql, String username, String ip) {
        return run(connectionId, sql, username, ip, null);
    }

    public QueryResult executeForExport(String connectionId, String sql, String username, String ip) {
        return run(connectionId, sql, username, ip, "[EXPORT] ");
    }

    private QueryResult run(String connectionId, String sql, String username, String ip, String auditPrefix) {
        QueryZenProperties.ConnectionDefinition def = registry.definition(connectionId);
        Dialect dialect = Dialect.forType(def.dbType());

        SqlGuard.validate(sql, def.dbType());
        String finalSql = dialect.applyRowLimit(sql, def.maxRows());

        long start = System.nanoTime();
        try (Connection conn = registry.get(connectionId).getConnection();
             PreparedStatement ps = conn.prepareStatement(finalSql)) {
            if (def.queryTimeoutSeconds() > 0) {
                ps.setQueryTimeout(def.queryTimeoutSeconds());
            }
            // 多取 1 行用于判断是否被截断
            ps.setMaxRows(def.maxRows() + 1);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();

                List<QueryResult.Column> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(new QueryResult.Column(md.getColumnName(i), md.getColumnTypeName(i)));
                }

                List<List<Object>> rows = new ArrayList<>();
                int cap = def.maxRows() + 1;
                for (int i = 0; i < cap && rs.next(); i++) {
                    rows.add(readRow(rs, colCount));
                }
                boolean truncated = rows.size() > def.maxRows();
                if (truncated) {
                    rows.remove(rows.size() - 1);
                }

                long elapsed = (System.nanoTime() - start) / 1_000_000;
                String auditedSql = auditPrefix == null ? sql : auditPrefix + sql;
                auditService.record(username, ip, auditedSql, rows.size(), elapsed, null);
                return new QueryResult(columns, rows, rows.size(), truncated, finalSql, elapsed);
            }
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            String auditedSql = auditPrefix == null ? sql : auditPrefix + sql;
            auditService.record(username, ip, auditedSql, 0, elapsed, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new QueryException("查询执行失败: " + e.getMessage(), e);
        }
    }

    public record TableInfo(String owner, String tableName) {}

    public List<TableInfo> listTables(String connectionId) {
        QueryZenProperties.ConnectionDefinition def = registry.definition(connectionId);
        Dialect dialect = Dialect.forType(def.dbType());

        String sql = dialect.listTablesSql();
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = registry.get(connectionId).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (def.queryTimeoutSeconds() > 0) {
                ps.setQueryTimeout(def.queryTimeoutSeconds());
            }
            while (rs.next()) {
                tables.add(new TableInfo(rs.getString(1), rs.getString(2)));
            }
        } catch (Exception e) {
            throw new QueryException("获取表清单失败: " + e.getMessage(), e);
        }
        return tables;
    }

    private List<Object> readRow(ResultSet rs, int colCount) throws SQLException {
        List<Object> row = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            Object v = rs.getObject(i);
            if (v instanceof Timestamp t) {
                row.add(t.toLocalDateTime().toString());
            } else if (v instanceof java.sql.Date d) {
                row.add(d.toLocalDate().toString());
            } else if (v instanceof Clob c) {
                row.add(c.getSubString(1, (int) Math.min(c.length(), 4000)));
            } else {
                row.add(v);
            }
        }
        return row;
    }
}