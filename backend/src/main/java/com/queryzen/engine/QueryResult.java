package com.queryzen.engine;

import java.util.List;

public record QueryResult(
        List<Column> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        String finalSql,
        long elapsedMs
) {
    public record Column(String name, String jdbcType) {}
}