package com.queryzen.dialect;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitorAdapter;

import java.util.List;

/**
 * 只读 SQL 白名单校验（应用层防写核心）。
 *
 * 职责：把用户提交的 SQL 用 Druid 解析成 AST，
 * 只放行单条 SELECT/WITH..SELECT，其余（DDL/DML/存储过程/多语句/FOR UPDATE）一律拦截。
 * 若你新增数据库类型或调整拦截规则，请同时更新 {@code SqlGuardTest}。
 */
public final class SqlGuard {

    private SqlGuard() {}

    public static void validate(String sql, String dbType) {
        if (sql == null || sql.trim().isEmpty()) throw new IllegalArgumentException("SQL 不能为空");

        // 第一层：正则快过滤，拦掉明显危险的会话/权限/DDL 前缀
        String upper = sql.trim().toUpperCase();
        if (upper.matches("(CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|AUDIT|NOAUDIT|SET\\s+ROLE).*")) {
            throw new IllegalArgumentException("语句类型不被允许: DDL/权限/会话操作");
        }

        // 第二层：AST 精确校验；statements.size()>1 即多语句，禁止
        List<SQLStatement> statements = parse(sql, dbType);
        if (statements.isEmpty()) throw new IllegalArgumentException("没有可执行的语句");
        if (statements.size() > 1) throw new IllegalArgumentException("一次只允许执行一条语句");

        SQLStatement stmt = statements.get(0);
        if (!(stmt instanceof SQLSelectStatement)) {
            throw new IllegalArgumentException("语句类型不被允许: " + stmt.getClass().getSimpleName());
        }
        checkForUpdate((SQLSelectStatement) stmt);
    }

    private static List<SQLStatement> parse(String sql, String dbType) {
        try {
            return SQLUtils.parseStatements(sql, DbType.of(dbType));
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL 解析失败: " + e.getMessage());
        }
    }

    /** 阻止 SELECT ... FOR UPDATE（可能对数据行加锁，违背只读语义）。 */
    private static void checkForUpdate(SQLSelectStatement select) {
        final boolean[] found = {false};
        select.accept(new OracleASTVisitorAdapter() {
            @Override
            public boolean visit(SQLSelectQueryBlock x) {
                if (x.isForUpdate()) found[0] = true;
                return true;
            }
        });
        if (found[0]) {
            throw new IllegalArgumentException("语句类型不被允许: SELECT ... FOR UPDATE（可能锁定数据行）");
        }
    }
}