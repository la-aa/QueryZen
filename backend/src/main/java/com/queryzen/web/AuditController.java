package com.queryzen.web;

import com.queryzen.audit.AuditRecord;
import com.queryzen.audit.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计接口：/verify 校验哈希链完整性；/entries 分页查询审计记录（默认最近 50 条）。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/verify")
    public AuditService.VerifyResult verify() {
        return auditService.verifyChain();
    }

    @GetMapping("/entries")
    public List<AuditRecord> entries(@RequestParam(defaultValue = "50") int limit) {
        return auditService.recent(Math.min(Math.max(limit, 1), 1000));
    }
}