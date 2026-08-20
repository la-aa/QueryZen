package com.queryzen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "queryzen")
public record QueryZenProperties(
        List<ConnectionDefinition> connections,
        AuditProperties audit,
        List<UserAccount> users
) {
    public record ConnectionDefinition(
            String id,
            String name,
            String dbType,
            String jdbcUrl,
            String username,
            String password,
            String schema,
            int maxRows,
            int queryTimeoutSeconds
    ) {
        public ConnectionDefinition {
            if (maxRows <= 0) maxRows = 1000;
            if (queryTimeoutSeconds <= 0) queryTimeoutSeconds = 30;
        }
    }

    public record AuditProperties(DatasourceProps writer, DatasourceProps reader, String schema) {
        public AuditProperties {
            schema = schema == null || schema.isBlank() ? "AUDIT_OWNER" : schema.toUpperCase();
            if (writer == null) writer = new DatasourceProps(null, null, null);
            if (reader == null) reader = writer;
        }
    }

    public record DatasourceProps(String jdbcUrl, String username, String password) {}

    public record UserAccount(String username, String passwordSha256, List<String> roles) {}
}