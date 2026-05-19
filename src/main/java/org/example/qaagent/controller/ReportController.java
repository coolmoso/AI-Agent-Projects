package org.example.qaagent.controller;

import org.example.qaagent.model.MetricsReport;
import org.example.qaagent.service.MetricsCollector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final MetricsCollector metricsCollector;

    public ReportController(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping("/report")
    public ResponseEntity<MetricsReport> getReport() {
        return ResponseEntity.ok(metricsCollector.generateReport());
    }

    @GetMapping(value = "/report/csv", produces = "text/csv")
    public ResponseEntity<String> getReportCSV() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(metricsCollector.exportCSV());
    }
}
