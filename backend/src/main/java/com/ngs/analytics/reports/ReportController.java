package com.ngs.analytics.reports;

import com.ngs.analytics.auth.SecurityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final FastqReportService fastqReportService;

    public ReportController(ReportService reportService, FastqReportService fastqReportService) {
        this.reportService = reportService;
        this.fastqReportService = fastqReportService;
    }

    @GetMapping("/{sampleId}")
    public Map<String, Object> json(@PathVariable UUID sampleId) {
        return reportService.buildJson(sampleId, SecurityUtils.currentUser());
    }

    @GetMapping(value = "/{sampleId}/csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@PathVariable UUID sampleId) {
        byte[] body = reportService.buildCsv(sampleId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + sampleId + ".csv\"")
                .body(body);
    }

    @GetMapping(value = "/{sampleId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID sampleId) {
        byte[] body = reportService.buildPdf(sampleId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + sampleId + ".pdf\"")
                .body(body);
    }

    @GetMapping(value = "/{sampleId}/fastq/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> fastqHtml(@PathVariable UUID sampleId) {
        byte[] body = fastqReportService.buildHtml(sampleId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"fastq-" + sampleId + ".html\"")
                .body(body);
    }

    @GetMapping(value = "/{sampleId}/fastq/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> fastqPdf(@PathVariable UUID sampleId) {
        byte[] body = fastqReportService.buildPdf(sampleId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fastq-" + sampleId + ".pdf\"")
                .body(body);
    }
}
