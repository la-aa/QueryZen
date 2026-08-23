package com.queryzen.dialect;

/**
 * Oracle 方言实现。
 * 核心是 {@link #applyRowLimit}：把用户 SQL 包进内外层 ROWNUM 限制，
 * 加上 JDBC setMaxRows 双保险，防止一次查询拖垮全表。
 * 新增数据库时，在 {@link Dialect#forType} 注册对应实现即可，主流程无需改动。
 */
public class OracleDialect implements Dialect {

    @Override
    public String dbType() {
        return "oracle";
    }

    @Override
    public boolean isWithSelect(String sql) {
        return sql.stripLeading().matches("(?i)WITH\\s.*");
    }

    @Override
    public String applyRowLimit(String sql, int maxRows) {
        String s = sql.strip();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).stripTrailing();
        }
        if (isWithSelect(s)) {
            return s;
        }
        return "SELECT * FROM (\n" + s + "\n) queryzen_t WHERE ROWNUM <= " + maxRows;
    }

    @Override
    public String listTablesSql() {
        return "SELECT DISTINCT t.OWNER, t.TABLE_NAME "
                + "FROM USER_TAB_PRIVS p JOIN ALL_TABLES t "
                + "ON t.OWNER = p.OWNER AND t.TABLE_NAME = p.TABLE_NAME "
                + "ORDER BY t.OWNER, t.TABLE_NAME";
    }
}