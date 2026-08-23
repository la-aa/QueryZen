package com.queryzen.web;

import com.queryzen.config.AuthInterceptor;
import com.queryzen.config.AuthService;
import com.queryzen.engine.ExportService;
import com.queryzen.engine.QueryResult;
import com.queryzen.engine.QueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 查询接口：POST /api/query 执行只读查询；POST /api/query/export 导出 XLSX。
 * 审计中导出会带 [EXPORT] 前缀，便于区分「日常查询」与「走数据」两类行为。
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;
    private final ExportService exportService;

    public QueryController(QueryService queryService, ExportService exportService) {
        this.queryService = queryService;
        this.exportService = exportService;
    }

    public record QueryRequest(String connectionId, String sql) {}

    @PostMapping
    public QueryResult run(@RequestBody QueryRequest req, HttpServletRequest http) {
        AuthService.Session session =
                (AuthService.Session) http.getAttribute(AuthInterceptor.ATTR_SESSION);
        return queryService.execute(req.connectionId(), req.sql(), session.username(), clientIp(http));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody QueryRequest req, HttpServletRequest http) {
        AuthService.Session session =
                (AuthService.Session) http.getAttribute(AuthInterceptor.ATTR_SESSION);
        QueryResult result = queryService.executeForExport(
                req.connectionId(), req.sql(), session.username(), clientIp(http));

        byte[] data = exportService.toXlsx(result);
        String stamp = Instant.now().toString().substring(0, 19).replace(':', '-');
        String filename = "queryzen-export-" + stamp + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}