package com.ngs.analytics.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.*;
import com.ngs.analytics.projects.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ProjectService projectService;
    private final FastqMetricsRepository fastqMetricsRepository;
    private final VcfMetricsRepository vcfMetricsRepository;
    private final ObjectMapper objectMapper;

    public ReportService(
            ProjectService projectService,
            FastqMetricsRepository fastqMetricsRepository,
            VcfMetricsRepository vcfMetricsRepository,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.fastqMetricsRepository = fastqMetricsRepository;
        this.vcfMetricsRepository = vcfMetricsRepository;
        this.objectMapper = objectMapper;
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
                .ifPresent(m -> {
                    Map<String, Object> fastq = new LinkedHashMap<>();
                    fastq.put("readCount", m.getReadCount());
                    fastq.put("avgLength", m.getAvgLength());
                    fastq.put("gcContent", m.getGcContent());
                    fastq.put("meanQuality", m.getMeanQuality());
                    fastq.put("duplicationRate", m.getDuplicationRate());
                    report.put("fastq", fastq);
                });

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
        sb.append("sample,name,").append(json.get("sampleName")).append('\n');
        if (json.get("fastq") instanceof Map<?, ?> fastq) {
            fastq.forEach((k, v) -> sb.append("fastq,").append(k).append(',').append(v).append('\n'));
        }
        if (json.get("vcf") instanceof Map<?, ?> vcf) {
            vcf.forEach((k, v) -> sb.append("vcf,").append(k).append(',').append(v).append('\n'));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildPdf(UUID sampleId, UserAccount owner) {
        Map<String, Object> json = buildJson(sampleId, owner);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            document.add(new Paragraph("GENOMETRICS Report", title));
            document.add(new Paragraph("Sample: " + json.get("sampleName"), body));
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json), body));
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build PDF: " + ex.getMessage());
        }
    }
}
