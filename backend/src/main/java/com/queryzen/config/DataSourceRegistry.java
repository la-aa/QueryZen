package com.queryzen.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataSourceRegistry {

    private final Map<String, HikariDataSource> sources = new LinkedHashMap<>();
    private final Map<String, QueryZenProperties.ConnectionDefinition> definitions = new LinkedHashMap<>();

    public DataSourceRegistry(QueryZenProperties props) {
        for (QueryZenProperties.ConnectionDefinition def : props.connections()) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(def.jdbcUrl());
            cfg.setUsername(def.username());
            cfg.setPassword(def.password());
            cfg.setDriverClassName("oracle.jdbc.OracleDriver");
            cfg.setReadOnly(true);
            cfg.setConnectionTimeout(10000);
            cfg.setMinimumIdle(0);
            cfg.setMaximumPoolSize(4);
            if (def.schema() != null && !def.schema().isBlank()) {
                cfg.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + def.schema());
            }
            sources.put(def.id(), new HikariDataSource(cfg));
            definitions.put(def.id(), def);
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
        return List.copyOf(definitions.values());
    }

    @PreDestroy
    void close() {
        sources.values().forEach(HikariDataSource::close);
    }
}