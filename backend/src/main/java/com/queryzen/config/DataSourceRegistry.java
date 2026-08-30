package com.queryzen.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读数据源注册表（按 application.yml 的 queryzen.connections 初始化）。
 * 强制 readOnly=true；可选 CURRENT_SCHEMA 切换默认 Schema（权限只读账号一般固定一个 Schema）。
 * 新增/修改连接后重启后端生效，无需其它迁移。
 */
@Component
public class DataSourceRegistry {

    private final Map<String, HikariDataSource> sources = new LinkedHashMap<>();
    private final Map<String, QueryZenProperties.ConnectionDefinition> definitions = new LinkedHashMap<>();

    public DataSourceRegistry(QueryZenProperties props) {
        for (QueryZenProperties.ConnectionDefinition def : props.getConnections()) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(def.getJdbcUrl());
            cfg.setUsername(def.getUsername());
            cfg.setPassword(def.getPassword());
            cfg.setDriverClassName("oracle.jdbc.OracleDriver");
            cfg.setReadOnly(true);
            cfg.setConnectionTimeout(10000);
            cfg.setMinimumIdle(0);
            cfg.setMaximumPoolSize(4);
            if (def.getSchema() != null && !def.getSchema().trim().isEmpty()) {
                cfg.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + def.getSchema());
            }
            sources.put(def.getId(), new HikariDataSource(cfg));
            definitions.put(def.getId(), def);
        }
    }

    public HikariDataSource get(String id) {
        HikariDataSource ds = sources.get(id);
        if (ds == null) throw new IllegalArgumentException("未知的连接: " + id);
        return ds;
    }

    public QueryZenProperties.ConnectionDefinition definition(String id) {
        QueryZenProperties.ConnectionDefinition def = definitions.get(id);
        if (def == null) throw new IllegalArgumentException("未知的连接: " + id);
        return def;
    }

    public List<QueryZenProperties.ConnectionDefinition> definitions() {
        return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
    }

    @PreDestroy
    void close() {
        sources.values().forEach(HikariDataSource::close);
    }
}