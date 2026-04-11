package com.croh.reporting;

import com.croh.reporting.dto.ReportExecutionResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ReportService reportService;

    public ExportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{id}")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<ReportExecutionResponse> getExport(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        ReportExecution execution = reportService.getExport(id);
        // Object-level authorization: only the executor can retrieve their export
        if (!execution.getExecutedBy().equals(actor.accountId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(new ReportExecutionResponse(
                execution.getId(), execution.getTemplateId(), execution.getFilters(),
                execution.getOutputFormat(), execution.getStatus(), execution.getResultData(),
                execution.getExportFilePath(), execution.getExecutedBy(),
                execution.getCreatedAt(), execution.getCompletedAt()));
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
