package com.queryzen.dialect;

public interface Dialect {

    String dbType();

    boolean isWithSelect(String sql);

    String applyRowLimit(String sql, int maxRows);

    String listTablesSql();

    static Dialect forType(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "oracle" -> new OracleDialect();
            default -> throw new IllegalArgumentException("暂不支持该数据库类型: " + dbType);
        };
    }
}