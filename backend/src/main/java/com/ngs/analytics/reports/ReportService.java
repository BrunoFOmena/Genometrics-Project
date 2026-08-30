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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ProjectService projectService;
    private final FastqMetricsRepository fastqMetricsRepository;
    private final VcfMetricsRepository vcfMetricsRepository;
    private final FastqMetricsMapper fastqMetricsMapper;

    public ReportService(
            ProjectService projectService,
            FastqMetricsRepository fastqMetricsRepository,
            VcfMetricsRepository vcfMetricsRepository,
            FastqMetricsMapper fastqMetricsMapper
    ) {
        this.projectService = projectService;
        this.fastqMetricsRepository = fastqMetricsRepository;
        this.vcfMetricsRepository = vcfMetricsRepository;
        this.fastqMetricsMapper = fastqMetricsMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildJson(UUID sampleId, UserAccount owner) {
        Sample sample = projectService.getSampleOwned(sampleId, owner);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sampleId", sample.getId().toString());
        report.put("sampleName", sample.getName());
        report.put("fastaReferenceName", sample.getFastaReferenceName());

        fastqMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(sampleId, AnalysisStatus.DONE)
                .ifPresent(m -> report.put("fastq", fastqMetricsMapper.toApiMap(m)));

        vcfMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(sampleId, AnalysisStatus.DONE)
                .ifPresent(m -> {
                    Map<String, Object> vcf = new LinkedHashMap<>();
                    vcf.put("variantCount", m.getVariantCount());
                    vcf.put("snpCount", m.getSnpCount());
                    vcf.put("indelCount", m.getIndelCount());
                    vcf.put("tsTvRatio", m.getTsTvRatio());
                    vcf.put("meanQual", m.getMeanQual());
                    vcf.put("meanDp", m.getMeanDp());
                    report.put("vcf", vcf);
                });

        if (!report.containsKey("fastq") && !report.containsKey("vcf")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No completed metrics for report");
        }
        return report;
    }

    public byte[] buildCsv(UUID sampleId, UserAccount owner) {
        Map<String, Object> json = buildJson(sampleId, owner);
        StringBuilder sb = new StringBuilder("section,metric,value\n");
        sb.append("sample,name,").append(csvEscape(String.valueOf(json.get("sampleName")))).append('\n');
        if (json.get("fastq") instanceof Map<?, ?> fastq) {
            appendFastqCsv(sb, fastq);
        }
        if (json.get("vcf") instanceof Map<?, ?> vcf) {
            vcf.forEach((k, v) -> sb.append("vcf,").append(k).append(',').append(csvEscape(String.valueOf(v))).append('\n'));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private void appendFastqCsv(StringBuilder sb, Map<?, ?> fastq) {
        for (Map.Entry<?, ?> e : fastq.entrySet()) {
            String key = String.valueOf(e.getKey());
            Object val = e.getValue();
            if ("qc".equals(key) && val instanceof Map<?, ?> qc) {
                sb.append("fastq,qcOverall,").append(csvEscape(String.valueOf(qc.get("overall")))).append('\n');
                if (qc.get("checks") instanceof List<?> checks) {
                    for (Object row : checks) {
                        if (row instanceof Map<?, ?> check) {
                            sb.append("fastq_qc,")
                                    .append(csvEscape(String.valueOf(check.get("id")))).append(',')
                                    .append(csvEscape(String.valueOf(check.get("status")))).append(',')
                                    .append(csvEscape(String.valueOf(check.get("message"))))
                                    .append('\n');
                        }
                    }
                }
            } else if ("recommendations".equals(key) && val instanceof List<?> recs) {
                for (Object row : recs) {
                    if (row instanceof Map<?, ?> rec) {
                        sb.append("fastq_rec,")
                                .append(csvEscape(String.valueOf(rec.get("title")))).append(',')
                                .append(csvEscape(String.valueOf(rec.get("severity")))).append(',')
                                .append(csvEscape(String.valueOf(rec.get("detail"))))
                                .append('\n');
                    }
                }
            } else if (val instanceof Number || val instanceof Boolean || val instanceof String) {
                sb.append("fastq,").append(key).append(',').append(csvEscape(String.valueOf(val))).append('\n');
            }
        }
    }

    public byte[] buildPdf(UUID sampleId, UserAccount owner) {
        Map<String, Object> json = buildJson(sampleId, owner);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            document.add(new Paragraph("GENOMETRICS Report", title));
            document.add(new Paragraph("Sample: " + json.get("sampleName"), body));
            document.add(new Paragraph(" ", body));

            if (json.get("fastq") instanceof Map<?, ?> fastq) {
                document.add(new Paragraph("FASTQ metrics", heading));
                appendScalarLines(document, body, fastq, List.of(
                        "readCount", "avgLength", "gcContent", "meanQuality", "duplicationRate",
                        "pairedEnd", "readCountR1", "readCountR2"
                ));
                if (fastq.get("qc") instanceof Map<?, ?> qc) {
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
                if (fastq.get("recommendations") instanceof List<?> recs) {
                    document.add(new Paragraph("Recommendations", heading));
                    for (Object row : recs) {
                        if (row instanceof Map<?, ?> rec) {
                            document.add(new Paragraph(
                                    rec.get("title") + " (" + rec.get("severity") + "): " + rec.get("detail"),
                                    body));
                        }
                    }
                }
                document.add(new Paragraph(" ", body));
            }

            if (json.get("vcf") instanceof Map<?, ?> vcf) {
                document.add(new Paragraph("VCF metrics", heading));
                vcf.forEach((k, v) -> {
                    try {
                        document.add(new Paragraph(k + ": " + v, body));
                    } catch (Exception ignored) {
                    }
                });
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build PDF: " + ex.getMessage());
        }
    }

    private void appendScalarLines(Document document, Font body, Map<?, ?> map, List<String> keys) throws Exception {
        for (String key : keys) {
            if (map.containsKey(key)) {
                document.add(new Paragraph(key + ": " + map.get(key), body));
            }
        }
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
