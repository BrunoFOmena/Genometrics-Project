package com.ngs.analytics.analytics;

import com.ngs.analytics.analytics.AnalyticsDtos.Filters;
import com.ngs.analytics.analytics.AnalyticsDtos.QueryRequest;
import com.ngs.analytics.analytics.AnalyticsDtos.QueryResponse;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsQueryService {

    private static final Set<String> DIMENSIONS =
            Set.of("project", "sample", "analysis", "metric", "status", "fileType");
    private static final Set<String> AGGREGATIONS = Set.of("NONE", "AVG", "SUM", "MIN", "MAX");

    private final MetricFactRepository metricFactRepository;
    private final MetricCatalog catalog;

    public AnalyticsQueryService(
            MetricFactRepository metricFactRepository,
            MetricCatalog catalog
    ) {
        this.metricFactRepository = metricFactRepository;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public QueryResponse query(QueryRequest request, UUID ownerId) {
        validate(request);
        int limit = request.limit() == null ? 200 : request.limit();
        Filters filters = request.filters();
        String aggregation = normalizedAggregation(request.aggregation());
        boolean aggregate = !"NONE".equals(aggregation);
        int fetchLimit = aggregate ? 10_001 : limit + 1;
        boolean filterSamples = filters != null && filters.sampleIds() != null && !filters.sampleIds().isEmpty();
        boolean filterStatuses = filters != null && filters.statuses() != null && !filters.statuses().isEmpty();
        List<UUID> sampleIds = sampleIds(filters);
        List<AnalysisStatus> statuses = statuses(filters);
        List<MetricFact> facts = metricFactRepository.queryOwned(
                ownerId,
                request.metrics(),
                filters == null ? null : filters.projectId(),
                filterSamples,
                sampleIds,
                filterStatuses,
                statuses,
                filters == null ? null : filters.from(),
                filters == null ? null : filters.to(),
                PageRequest.of(0, fetchLimit)
        );
        if (aggregate && facts.size() > 10_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Query scope is too broad for aggregation; add project, sample, or date filters");
        }
        List<RowValue> values = facts.stream()
                .limit(aggregate ? 10_000 : limit)
                .map(fact -> new RowValue(fact.getAnalysis(), fact))
                .toList();
        List<Map<String, Object>> allRows = "NONE".equals(aggregation)
                ? values.stream().map(this::flatRow).toList()
                : aggregate(values, request.dimensions(), aggregation);
        List<Map<String, Object>> rows = allRows.stream().limit(limit).toList();
        List<String> columns = rows.isEmpty() ? defaultColumns(request, aggregation)
                : List.copyOf(rows.getFirst().keySet());
        long total = aggregate ? allRows.size() : metricFactRepository.countOwned(
                ownerId,
                request.metrics(),
                filters == null ? null : filters.projectId(),
                filterSamples,
                sampleIds,
                filterStatuses,
                statuses,
                filters == null ? null : filters.from(),
                filters == null ? null : filters.to()
        );
        return new QueryResponse(
                columns,
                rows,
                Math.toIntExact(Math.min(total, Integer.MAX_VALUE)),
                total > rows.size()
        );
    }

    private void validate(QueryRequest request) {
        request.metrics().forEach(key -> {
            if (!catalog.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown metric: " + key);
            }
        });
        List<String> dimensions = request.dimensions() == null ? List.of() : request.dimensions();
        dimensions.forEach(dimension -> {
            if (!DIMENSIONS.contains(dimension)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown dimension: " + dimension);
            }
        });
        String aggregation = normalizedAggregation(request.aggregation());
        if (!AGGREGATIONS.contains(aggregation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown aggregation: " + aggregation);
        }
        if (!"NONE".equals(aggregation)) {
            request.metrics().forEach(key -> {
                if (!catalog.require(key).aggregations().contains(aggregation)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            aggregation + " is not allowed for metric " + key);
                }
            });
        }
    }

    private Map<String, Object> flatRow(RowValue value) {
        Analysis analysis = value.analysis();
        MetricFact fact = value.fact();
        Map<String, Object> row = baseDimensions(analysis);
        row.put("metric", fact.getMetricKey());
        row.put("value", fact.getNumericValue());
        row.put("unit", fact.getUnit());
        return row;
    }

    private List<Map<String, Object>> aggregate(
            List<RowValue> values, List<String> requestedDimensions, String aggregation
    ) {
        List<String> dimensions = requestedDimensions == null || requestedDimensions.isEmpty()
                ? List.of("metric") : requestedDimensions;
        Map<String, List<RowValue>> groups = values.stream().collect(Collectors.groupingBy(
                value -> dimensions.stream()
                        .map(dimension -> String.valueOf(dimensionValue(value, dimension)))
                        .collect(Collectors.joining("\u001f")),
                LinkedHashMap::new,
                Collectors.toList()
        ));

        return groups.values().stream().map(group -> {
            RowValue first = group.getFirst();
            Map<String, Object> row = new LinkedHashMap<>();
            dimensions.forEach(d -> row.put(d, dimensionValue(first, d)));
            DoubleSummaryStatistics stats = group.stream()
                    .map(RowValue::fact)
                    .map(MetricFact::getNumericValue)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            double result = switch (aggregation) {
                case "SUM" -> stats.getSum();
                case "MIN" -> stats.getMin();
                case "MAX" -> stats.getMax();
                default -> stats.getAverage();
            };
            row.put("value", stats.getCount() == 0 ? null : result);
            row.put("aggregation", aggregation);
            row.put("count", stats.getCount());
            return row;
        }).toList();
    }

    private Object dimensionValue(RowValue value, String dimension) {
        Analysis analysis = value.analysis();
        return switch (dimension) {
            case "project" -> analysis.getSample().getProject().getName();
            case "sample" -> analysis.getSample().getName();
            case "analysis" -> analysis.getId();
            case "status" -> analysis.getStatus().name();
            case "fileType" -> analysis.getFileAsset().getFileType().name();
            default -> value.fact().getMetricKey();
        };
    }

    private Map<String, Object> baseDimensions(Analysis analysis) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", analysis.getSample().getProject().getId());
        row.put("project", analysis.getSample().getProject().getName());
        row.put("sampleId", analysis.getSample().getId());
        row.put("sample", analysis.getSample().getName());
        row.put("analysisId", analysis.getId());
        row.put("status", analysis.getStatus().name());
        row.put("fileType", analysis.getFileAsset().getFileType().name());
        row.put("finishedAt", analysis.getFinishedAt());
        return row;
    }

    private List<String> defaultColumns(QueryRequest request, String aggregation) {
        if ("NONE".equals(aggregation)) {
            return List.of("projectId", "project", "sampleId", "sample", "analysisId",
                    "status", "fileType", "finishedAt", "metric", "value", "unit");
        }
        List<String> columns = new ArrayList<>(
                request.dimensions() == null || request.dimensions().isEmpty()
                        ? List.of("metric") : request.dimensions());
        columns.addAll(List.of("value", "aggregation", "count"));
        return columns;
    }

    private String normalizedAggregation(String aggregation) {
        return aggregation == null || aggregation.isBlank() ? "NONE" : aggregation.toUpperCase(Locale.ROOT);
    }

    private List<UUID> sampleIds(Filters filters) {
        return filters == null || filters.sampleIds() == null || filters.sampleIds().isEmpty()
                ? List.of(new UUID(0, 0)) : filters.sampleIds();
    }

    private List<AnalysisStatus> statuses(Filters filters) {
        if (filters == null || filters.statuses() == null || filters.statuses().isEmpty()) {
            return List.of(AnalysisStatus.DONE);
        }
        try {
            return filters.statuses().stream().map(AnalysisStatus::valueOf).toList();
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown analysis status");
        }
    }

    private record RowValue(Analysis analysis, MetricFact fact) {}
}
