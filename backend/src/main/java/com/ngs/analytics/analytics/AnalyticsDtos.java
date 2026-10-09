package com.ngs.analytics.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record QueryRequest(
            @NotEmpty List<String> metrics,
            List<String> dimensions,
            Filters filters,
            String aggregation,
            @Min(1) @Max(1000) Integer limit
    ) {}

    public record Filters(
            UUID projectId,
            List<UUID> sampleIds,
            List<String> statuses,
            Instant from,
            Instant to
    ) {}

    public record QueryResponse(
            List<String> columns,
            List<Map<String, Object>> rows,
            int total,
            boolean truncated
    ) {}
}
