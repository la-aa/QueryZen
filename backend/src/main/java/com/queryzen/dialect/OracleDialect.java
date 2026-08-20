package com.queryzen.dialect;

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