package com.ngs.analytics.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.auth.SecurityUtils;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.*;
import com.ngs.analytics.fastq.FastqMetricsMapper;
import com.ngs.analytics.projects.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MetricsController {

    private final ProjectService projectService;
    private final FastqMetricsRepository fastqMetricsRepository;
    private final VcfMetricsRepository vcfMetricsRepository;
    private final AnalysisService analysisService;
    private final SampleRepository sampleRepository;
    private final FastqMetricsMapper fastqMetricsMapper;
    private final ObjectMapper objectMapper;

    public MetricsController(
            ProjectService projectService,
            FastqMetricsRepository fastqMetricsRepository,
            VcfMetricsRepository vcfMetricsRepository,
            AnalysisService analysisService,
            SampleRepository sampleRepository,
            FastqMetricsMapper fastqMetricsMapper,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.fastqMetricsRepository = fastqMetricsRepository;
        this.vcfMetricsRepository = vcfMetricsRepository;
        this.analysisService = analysisService;
        this.sampleRepository = sampleRepository;
        this.fastqMetricsMapper = fastqMetricsMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/analyses/{analysisId}")
    public ProjectDtosLike analysis(@PathVariable UUID analysisId) {
        Analysis a = analysisService.get(analysisId, SecurityUtils.currentUser().getId());
        return new ProjectDtosLike(
                a.getId().toString(),
                a.getSample().getId().toString(),
                a.getFileAsset().getId().toString(),
                a.getStatus().name(),
                a.getEngine(),
                a.getErrorMessage(),
                a.getCreatedAt(),
                a.getStartedAt(),
                a.getFinishedAt()
        );
    }

    @GetMapping("/analyses")
    public List<ProjectDtosLike> history() {
        return analysisService.historyForOwner(SecurityUtils.currentUser().getId()).stream()
                .map(a -> new ProjectDtosLike(
                        a.getId().toString(),
                        a.getSample().getId().toString(),
                        a.getFileAsset().getId().toString(),
                        a.getStatus().name(),
                        a.getEngine(),
                        a.getErrorMessage(),
                        a.getCreatedAt(),
                        a.getStartedAt(),
                        a.getFinishedAt()
                ))
                .toList();
    }

    @GetMapping("/samples/{sampleId}/metrics/fastq")
    public Map<String, Object> fastqMetrics(@PathVariable UUID sampleId) {
        projectService.getSampleOwned(sampleId, SecurityUtils.currentUser());
        FastqMetrics m = fastqMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(sampleId, AnalysisStatus.DONE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FASTQ metrics not available yet"));
        return mapFastq(m);
    }

    @GetMapping("/analyses/{analysisId}/metrics/fastq")
    public Map<String, Object> fastqMetricsByAnalysis(@PathVariable UUID analysisId) {
        Analysis analysis = analysisService.get(analysisId, SecurityUtils.currentUser().getId());
        if (analysis.getStatus() != AnalysisStatus.DONE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FASTQ metrics not available yet");
        }
        FastqMetrics m = fastqMetricsRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FASTQ metrics not available yet"));
        return mapFastq(m);
    }

    @GetMapping("/samples/{sampleId}/metrics/vcf")
    public Map<String, Object> vcfMetrics(@PathVariable UUID sampleId) {
        projectService.getSampleOwned(sampleId, SecurityUtils.currentUser());
        VcfMetrics m = vcfMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(sampleId, AnalysisStatus.DONE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VCF metrics not available yet"));
        return mapVcf(m);
    }

    @GetMapping("/analyses/{analysisId}/metrics/vcf")
    public Map<String, Object> vcfMetricsByAnalysis(@PathVariable UUID analysisId) {
        Analysis analysis = analysisService.get(analysisId, SecurityUtils.currentUser().getId());
        if (analysis.getStatus() != AnalysisStatus.DONE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VCF metrics not available yet");
        }
        VcfMetrics m = vcfMetricsRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VCF metrics not available yet"));
        return mapVcf(m);
    }

    @GetMapping("/samples/compare")
    public Map<String, Object> compare(@RequestParam UUID a, @RequestParam UUID b) {
        var user = SecurityUtils.currentUser();
        Sample sa = projectService.getSampleOwned(a, user);
        Sample sb = projectService.getSampleOwned(b, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleA", Map.of("id", sa.getId().toString(), "name", sa.getName()));
        result.put("sampleB", Map.of("id", sb.getId().toString(), "name", sb.getName()));

        Optional<FastqMetrics> fa = fastqMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(a, AnalysisStatus.DONE);
        Optional<FastqMetrics> fb = fastqMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(b, AnalysisStatus.DONE);
        if (fa.isPresent() && fb.isPresent()) {
            result.put("fastq", Map.of(
                    "gcContent", Map.of("a", fa.get().getGcContent(), "b", fb.get().getGcContent()),
                    "readCount", Map.of("a", fa.get().getReadCount(), "b", fb.get().getReadCount()),
                    "meanQuality", Map.of("a", fa.get().getMeanQuality(), "b", fb.get().getMeanQuality()),
                    "avgLength", Map.of("a", fa.get().getAvgLength(), "b", fb.get().getAvgLength())
            ));
        }

        Optional<VcfMetrics> va = vcfMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(a, AnalysisStatus.DONE);
        Optional<VcfMetrics> vb = vcfMetricsRepository
                .findTopByAnalysisSampleIdAndAnalysisStatusOrderByAnalysisFinishedAtDesc(b, AnalysisStatus.DONE);
        if (va.isPresent() && vb.isPresent()) {
            result.put("vcf", Map.of(
                    "snpCount", Map.of("a", va.get().getSnpCount(), "b", vb.get().getSnpCount()),
                    "indelCount", Map.of("a", va.get().getIndelCount(), "b", vb.get().getIndelCount()),
                    "tsTvRatio", Map.of("a", va.get().getTsTvRatio(), "b", vb.get().getTsTvRatio()),
                    "variantCount", Map.of("a", va.get().getVariantCount(), "b", vb.get().getVariantCount())
            ));
        }
        if (!result.containsKey("fastq") && !result.containsKey("vcf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Both samples need completed metrics of the same type");
        }
        return result;
    }

    @GetMapping("/samples")
    public List<Map<String, Object>> allSamples() {
        return sampleRepository.findByProjectOwnerIdOrderByCreatedAtDesc(SecurityUtils.currentUser().getId())
                .stream()
                .map(s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", s.getId().toString());
                    row.put("projectId", s.getProject().getId().toString());
                    row.put("name", s.getName());
                    row.put("notes", s.getNotes());
                    row.put("fastaReferenceName", s.getFastaReferenceName());
                    row.put("createdAt", s.getCreatedAt());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> mapFastq(FastqMetrics m) {
        return fastqMetricsMapper.toApiMap(m);
    }

    private Map<String, Object> mapVcf(VcfMetrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("analysisId", m.getAnalysis().getId().toString());
        map.put("variantCount", m.getVariantCount());
        map.put("snpCount", m.getSnpCount());
        map.put("indelCount", m.getIndelCount());
        map.put("mnpCount", m.getMnpCount());
        map.put("meanQual", m.getMeanQual());
        map.put("meanDp", m.getMeanDp());
        map.put("transitions", m.getTransitions());
        map.put("transversions", m.getTransversions());
        map.put("tsTvRatio", m.getTsTvRatio());
        map.put("passCount", m.getPassCount());
        map.put("failCount", m.getFailCount());
        map.put("chromosomeDistribution", readJson(m.getChromosomeDistributionJson()));
        map.put("alleleFrequency", readJson(m.getAlleleFrequencyJson()));
        map.put("geneDistribution", readJson(m.getGeneDistributionJson()));
        map.put("filterDistribution", readJson(m.getFilterDistributionJson()));
        map.put("indelLengthDistribution", readJson(m.getIndelLengthDistributionJson()));
        map.put("tsTvByChromosome", readJson(m.getTsTvByChromosomeJson()));
        map.put("header", readJson(m.getHeaderJson()));
        map.put("qualHistogram", readJson(m.getQualHistogramJson()));
        map.put("dpHistogram", readJson(m.getDpHistogramJson()));
        return map;
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return json;
        }
    }

    public record ProjectDtosLike(
            String id,
            String sampleId,
            String fileAssetId,
            String status,
            String engine,
            String errorMessage,
            java.time.Instant createdAt,
            java.time.Instant startedAt,
            java.time.Instant finishedAt
    ) {
    }
}
