package com.flowlane.reporting;

import com.flowlane.reporting.ReportService.Overview;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public Overview overview(@PathVariable UUID workspaceId, @RequestParam(defaultValue = "6") int weeks) {
        return reports.overview(workspaceId, weeks);
    }

    @GetMapping(value = "/overview.csv", produces = "text/csv")
    public ResponseEntity<String> csv(@PathVariable UUID workspaceId, @RequestParam(defaultValue = "6") int weeks) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowlane-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.overviewCsv(workspaceId, weeks));
    }
}
