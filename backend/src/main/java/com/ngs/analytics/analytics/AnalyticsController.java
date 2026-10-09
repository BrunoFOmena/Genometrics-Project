package com.ngs.analytics.analytics;

import com.ngs.analytics.analytics.AnalyticsDtos.QueryRequest;
import com.ngs.analytics.analytics.AnalyticsDtos.QueryResponse;
import com.ngs.analytics.auth.SecurityUtils;
import com.ngs.analytics.domain.QcCheckFactRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final MetricCatalog catalog;
    private final AnalyticsQueryService queryService;
    private final AnalysisService analysisService;
    private final QcCheckFactRepository qcCheckFactRepository;

    public AnalyticsController(
            MetricCatalog catalog,
            AnalyticsQueryService queryService,
            AnalysisService analysisService,
            QcCheckFactRepository qcCheckFactRepository
    ) {
        this.catalog = catalog;
        this.queryService = queryService;
        this.analysisService = analysisService;
        this.qcCheckFactRepository = qcCheckFactRepository;
    }

    @GetMapping("/metrics")
    public List<MetricCatalog.Definition> metrics() {
        return catalog.all();
    }

    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return queryService.query(request, SecurityUtils.currentUser().getId());
    }

    @GetMapping("/analyses/{analysisId}/qc")
    public List<Map<String, Object>> qc(@PathVariable UUID analysisId) {
        analysisService.get(analysisId, SecurityUtils.currentUser().getId());
        return qcCheckFactRepository.findByAnalysisId(analysisId).stream().map(check -> Map.<String, Object>of(
                "key", check.getCheckKey(),
                "status", check.getStatus(),
                "value", check.getNumericValue() == null ? "" : check.getNumericValue(),
                "message", check.getMessage() == null ? "" : check.getMessage()
        )).toList();
    }
}
