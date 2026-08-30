package com.queryzen.engine;

import java.util.List;

public class QueryResult {
    private final List<Column> columns;
    private final List<List<Object>> rows;
    private final int rowCount;
    private final boolean truncated;
    private final String finalSql;
    private final long elapsedMs;

    public QueryResult(List<Column> columns, List<List<Object>> rows, int rowCount,
                       boolean truncated, String finalSql, long elapsedMs) {
        this.columns = columns;
        this.rows = rows;
        this.rowCount = rowCount;
        this.truncated = truncated;
        this.finalSql = finalSql;
        this.elapsedMs = elapsedMs;
    }

    public List<Column> getColumns() { return columns; }
    public List<List<Object>> getRows() { return rows; }
    public int getRowCount() { return rowCount; }
    public boolean isTruncated() { return truncated; }
    public String getFinalSql() { return finalSql; }
    public long getElapsedMs() { return elapsedMs; }

    public static class Column {
        private final String name;
        private final String jdbcType;

        public Column(String name, String jdbcType) {
            this.name = name;
            this.jdbcType = jdbcType;
        }

        public String getName() { return name; }
        public String getJdbcType() { return jdbcType; }
    }
}
