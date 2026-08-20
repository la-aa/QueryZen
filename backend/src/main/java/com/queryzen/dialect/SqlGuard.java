package com.queryzen.dialect;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitorAdapter;

import java.util.List;

public final class SqlGuard {

    private SqlGuard() {}

    public static void validate(String sql, String dbType) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 不能为空");

        String upper = sql.stripLeading().toUpperCase();
        if (upper.matches("(CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|AUDIT|NOAUDIT|SET\\s+ROLE).*")) {
            throw new IllegalArgumentException("语句类型不被允许: DDL/权限/会话操作");
        }

        List<SQLStatement> statements = parse(sql, dbType);
        if (statements.isEmpty()) throw new IllegalArgumentException("没有可执行的语句");
        if (statements.size() > 1) throw new IllegalArgumentException("一次只允许执行一条语句");

        SQLStatement stmt = statements.get(0);
        if (!(stmt instanceof SQLSelectStatement select)) {
            throw new IllegalArgumentException("语句类型不被允许: " + stmt.getClass().getSimpleName());
        }
        checkForUpdate(select);
    }

    private static List<SQLStatement> parse(String sql, String dbType) {
        try {
            return SQLUtils.parseStatements(sql, DbType.of(dbType));
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL 解析失败: " + e.getMessage());
        }
    }

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