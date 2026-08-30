package com.queryzen.audit;

public class AuditRecord {
    private final String username;
    private final String ip;
    private final String sqlText;
    private final int rowsReturned;
    private final long elapsedMs;
    private final String errorMsg;
    private final String ts;

    public AuditRecord(String username, String ip, String sqlText, int rowsReturned,
                       long elapsedMs, String errorMsg, String ts) {
        this.username = username;
        this.ip = ip;
        this.sqlText = sqlText;
        this.rowsReturned = rowsReturned;
        this.elapsedMs = elapsedMs;
        this.errorMsg = errorMsg;
        this.ts = ts;
    }

    public String getUsername() { return username; }
    public String getIp() { return ip; }
    public String getSqlText() { return sqlText; }
    public int getRowsReturned() { return rowsReturned; }
    public long getElapsedMs() { return elapsedMs; }
    public String getErrorMsg() { return errorMsg; }
    public String getTs() { return ts; }
}
