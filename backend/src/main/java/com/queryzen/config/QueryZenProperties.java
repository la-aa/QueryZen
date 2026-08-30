package com.queryzen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 绑定 application.yml 的 queryzen.* 配置：
 * connections=数据库连接；audit=审计库读写账号；users=配置文件内建账号（兜底 admin）。
 * 环境变量可覆盖：QUERYZEN_DB_* / QUERYZEN_AUDIT_*。
 */
@ConfigurationProperties(prefix = "queryzen")
public class QueryZenProperties {

    private List<ConnectionDefinition> connections;
    private AuditProperties audit;
    private List<UserAccount> users;

    /** Boot 2 为 JavaBean 绑定，原紧凑构造器的默认值/归一化逻辑统一放到绑定完成后执行。 */
    @PostConstruct
    void normalize() {
        if (connections != null) {
            for (ConnectionDefinition c : connections) {
                c.normalize();
            }
        }
        if (audit != null) {
            audit.normalize();
        }
    }

    public List<ConnectionDefinition> getConnections() { return connections; }
    public void setConnections(List<ConnectionDefinition> connections) { this.connections = connections; }

    public AuditProperties getAudit() { return audit; }
    public void setAudit(AuditProperties audit) { this.audit = audit; }

    public List<UserAccount> getUsers() { return users; }
    public void setUsers(List<UserAccount> users) { this.users = users; }

    public static class ConnectionDefinition {
        private String id;
        private String name;
        private String dbType;
        private String jdbcUrl;
        private String username;
        private String password;
        private String schema;
        private int maxRows;
        private int queryTimeoutSeconds;

        void normalize() {
            if (maxRows <= 0) maxRows = 1000;
            if (queryTimeoutSeconds <= 0) queryTimeoutSeconds = 30;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }

        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    }

    public static class AuditProperties {
        private DatasourceProps writer;
        private DatasourceProps reader;
        private String schema;

        void normalize() {
            schema = schema == null || schema.trim().isEmpty() ? "AUDIT_OWNER" : schema.toUpperCase();
            if (writer == null) writer = new DatasourceProps();
            if (reader == null) reader = writer;
        }

        public DatasourceProps getWriter() { return writer; }
        public void setWriter(DatasourceProps writer) { this.writer = writer; }

        public DatasourceProps getReader() { return reader; }
        public void setReader(DatasourceProps reader) { this.reader = reader; }

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
    }

    public static class DatasourceProps {
        private String jdbcUrl;
        private String username;
        private String password;

        public DatasourceProps() {}

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class UserAccount {
        private String username;
        private String passwordSha256;
        private List<String> roles;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPasswordSha256() { return passwordSha256; }
        public void setPasswordSha256(String passwordSha256) { this.passwordSha256 = passwordSha256; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
