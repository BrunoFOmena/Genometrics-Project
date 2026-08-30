package com.ngs.analytics.reports;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.*;
import com.ngs.analytics.fastq.FastqMetricsMapper;
import com.ngs.analytics.projects.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FastqReportService {

    private final ProjectService projectService;
    private final FastqMetricsRepository fastqMetricsRepository;
    private final FastqMetricsMapper fastqMetricsMapper;

    public FastqReportService(
            ProjectService projectService,
            FastqMetricsRepository fastqMetricsRepository,
            FastqMetricsMapper fastqMetricsMapper
    ) {
        this.projectService = projectService;
        this.fastqMetricsRepository = fastqMetricsRepository;
        this.fastqMetricsMapper = fastqMetricsMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadFastqReport(UUID sampleId, UserAccount owner) {
        Sample sample = projectService.getSampleOwned(sampleId, owner);
        FastqMetrics metrics = fastqMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(sampleId, AnalysisStatus.DONE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FASTQ metrics not available yet"));
        Map<String, Object> report = fastqMetricsMapper.toApiMap(metrics);
        report.put("sampleId", sample.getId().toString());
        report.put("sampleName", sample.getName());
        return report;
    }

    public byte[] buildHtml(UUID sampleId, UserAccount owner) {
        Map<String, Object> data = loadFastqReport(sampleId, owner);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>FASTQ Report</title>")
                .append("<style>body{font-family:sans-serif;margin:2rem;}")
                .append(".pass{color:#0f6a4d}.warn{color:#c45c26}.fail{color:#9b2c2c}")
                .append("table{border-collapse:collapse;margin:1rem 0}td,th{border:1px solid #ccc;padding:.4rem .6rem}")
                .append("</style></head><body>");
        html.append("<h1>GENOMETRICS FASTQ Report</h1>");
        html.append("<p><strong>Sample:</strong> ").append(esc(String.valueOf(data.get("sampleName")))).append("</p>");

        if (data.get("qc") instanceof Map<?, ?> qc) {
            html.append("<h2>Overall QC: <span class=\"")
                    .append(String.valueOf(qc.get("overall")).toLowerCase())
                    .append("\">").append(esc(String.valueOf(qc.get("overall")))).append("</span></h2>");
            html.append("<h3>Checks</h3><table><tr><th>Check</th><th>Status</th><th>Message</th></tr>");
            if (qc.get("checks") instanceof List<?> checks) {
                for (Object row : checks) {
                    if (row instanceof Map<?, ?> check) {
                        html.append("<tr><td>").append(esc(String.valueOf(check.get("id"))))
                                .append("</td><td class=\"").append(String.valueOf(check.get("status")).toLowerCase())
                                .append("\">").append(esc(String.valueOf(check.get("status"))))
                                .append("</td><td>").append(esc(String.valueOf(check.get("message"))))
                                .append("</td></tr>");
                    }
                }
            }
            html.append("</table>");
        }

        html.append("<h3>Headline metrics</h3><ul>");
        appendLi(html, "Read count", data.get("readCount"));
        appendLi(html, "Mean quality", data.get("meanQuality"));
        appendLi(html, "GC content", data.get("gcContent"));
        appendLi(html, "Duplication rate", data.get("duplicationRate"));
        appendLi(html, "Paired-end", data.get("pairedEnd"));
        if (Boolean.TRUE.equals(data.get("pairedEnd"))) {
            appendLi(html, "R1 reads", data.get("readCountR1"));
            appendLi(html, "R2 reads", data.get("readCountR2"));
        }
        html.append("</ul>");

        if (data.get("recommendations") instanceof List<?> recs && !recs.isEmpty()) {
            html.append("<h3>Recommendations</h3><ul>");
            for (Object row : recs) {
                if (row instanceof Map<?, ?> rec) {
                    html.append("<li><strong>").append(esc(String.valueOf(rec.get("title"))))
                            .append("</strong> (").append(esc(String.valueOf(rec.get("severity"))))
                            .append("): ").append(esc(String.valueOf(rec.get("detail")))).append("</li>");
                }
            }
            html.append("</ul>");
        }

        if (data.get("adapterHits") instanceof Map<?, ?> adapters) {
            html.append("<h3>Adapter summary</h3><p>Hits in ")
                    .append(esc(String.valueOf(adapters.get("fraction")))).append("% of sampled reads.</p>");
            if (adapters.get("adapters") instanceof List<?> adapterRows && !adapterRows.isEmpty()) {
                html.append("<table><tr><th>Adapter</th><th>Count</th></tr>");
                for (Object row : adapterRows) {
                    if (row instanceof Map<?, ?> a) {
                        html.append("<tr><td>").append(esc(String.valueOf(a.get("name"))))
                                .append("</td><td>").append(esc(String.valueOf(a.get("count"))))
                                .append("</td></tr>");
                    }
                }
                html.append("</table>");
            }
        }

        html.append("<p><em>For interactive charts, open the sample detail page in the GENOMETRICS UI.</em></p>");
        html.append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildPdf(UUID sampleId, UserAccount owner) {
        Map<String, Object> data = loadFastqReport(sampleId, owner);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("GENOMETRICS FASTQ Report", title));
            document.add(new Paragraph("Sample: " + data.get("sampleName"), body));
            document.add(new Paragraph(" ", body));

            if (data.get("qc") instanceof Map<?, ?> qc) {
                document.add(new Paragraph("Overall QC: " + qc.get("overall"), heading));
                if (qc.get("checks") instanceof List<?> checks) {
                    for (Object row : checks) {
                        if (row instanceof Map<?, ?> check) {
                            document.add(new Paragraph(
                                    check.get("id") + " [" + check.get("status") + "]: " + check.get("message"),
                                    body));
                        }
                    }
                }
            }

            document.add(new Paragraph("Headline metrics", heading));
            document.add(new Paragraph("Read count: " + data.get("readCount"), body));
            document.add(new Paragraph("Mean quality: " + data.get("meanQuality"), body));
            document.add(new Paragraph("GC content: " + data.get("gcContent"), body));
            document.add(new Paragraph("Duplication rate: " + data.get("duplicationRate"), body));
            if (Boolean.TRUE.equals(data.get("pairedEnd"))) {
                document.add(new Paragraph("Paired-end R1/R2: " + data.get("readCountR1") + " / "
                        + data.get("readCountR2"), body));
            }

            if (data.get("recommendations") instanceof List<?> recs) {
                document.add(new Paragraph("Recommendations", heading));
                for (Object row : recs) {
                    if (row instanceof Map<?, ?> rec) {
                        document.add(new Paragraph(
                                rec.get("title") + " (" + rec.get("severity") + "): " + rec.get("detail"),
                                body));
                    }
                }
            }

            if (data.get("adapterHits") instanceof Map<?, ?> adapters) {
                document.add(new Paragraph("Adapter hits: " + adapters.get("fraction") + "% of sampled reads", body));
            }

            document.add(new Paragraph("Open the GENOMETRICS UI for interactive charts.", body));
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build FASTQ PDF: " + ex.getMessage());
        }
    }

    private static void appendLi(StringBuilder html, String label, Object value) {
        html.append("<li><strong>").append(esc(label)).append(":</strong> ")
                .append(esc(String.valueOf(value))).append("</li>");
    }

    private static String esc(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
