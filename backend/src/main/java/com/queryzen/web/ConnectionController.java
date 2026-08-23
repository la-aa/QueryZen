package com.queryzen.web;

import com.queryzen.config.DataSourceRegistry;
import com.queryzen.config.QueryZenProperties;
import com.queryzen.engine.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 连接接口：列出可用连接（前端下拉选择），及某连接的可见表清单（侧栏展示）。
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final DataSourceRegistry registry;
    private final QueryService queryService;

    public ConnectionController(DataSourceRegistry registry, QueryService queryService) {
        this.registry = registry;
        this.queryService = queryService;
    }

    public record ConnectionView(String id, String name, String dbType, String schema, int maxRows) {}

    @GetMapping
    public List<ConnectionView> list() {
        return registry.definitions().stream()
                .map((QueryZenProperties.ConnectionDefinition d) ->
                        new ConnectionView(d.id(), d.name(), d.dbType(), d.schema(), d.maxRows()))
                .toList();
    }

    @GetMapping("/{id}/tables")
    public List<QueryService.TableInfo> tables(@PathVariable String id) {
        return queryService.listTables(id);
    }
}