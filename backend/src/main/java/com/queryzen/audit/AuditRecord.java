package com.queryzen.audit;

public record AuditRecord(
        String username,
        String ip,
        String sqlText,
        int rowsReturned,
        long elapsedMs,
        String errorMsg,
        String ts
) {}