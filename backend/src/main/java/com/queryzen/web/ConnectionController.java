package com.queryzen.web;

import com.queryzen.config.DataSourceRegistry;
import com.queryzen.config.QueryZenProperties;
import com.queryzen.engine.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

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

    public static class ConnectionView {
        private final String id;
        private final String name;
        private final String dbType;
        private final String schema;
        private final int maxRows;

        public ConnectionView(String id, String name, String dbType, String schema, int maxRows) {
            this.id = id;
            this.name = name;
            this.dbType = dbType;
            this.schema = schema;
            this.maxRows = maxRows;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDbType() { return dbType; }
        public String getSchema() { return schema; }
        public int getMaxRows() { return maxRows; }
    }

    @GetMapping
    public List<ConnectionView> list() {
        return registry.definitions().stream()
                .map((QueryZenProperties.ConnectionDefinition d) ->
                        new ConnectionView(d.getId(), d.getName(), d.getDbType(), d.getSchema(), d.getMaxRows()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/tables")
    public List<QueryService.TableInfo> tables(@PathVariable String id) {
        return queryService.listTables(id);
    }
}
