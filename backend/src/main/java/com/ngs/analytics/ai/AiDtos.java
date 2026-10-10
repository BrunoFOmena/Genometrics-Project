package com.ngs.analytics.ai;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AiDtos {

    private AiDtos() {}

    public record AskRequest(
            @NotBlank String question,
            UUID projectId,
            List<UUID> sampleIds,
            List<String> metrics
    ) {}

    public record Citation(
            UUID analysisId,
            UUID sampleId,
            String metric,
            Double value,
            String unit
    ) {}

    public record AskResponse(
            UUID interactionId,
            String answer,
            String model,
            List<String> tools,
            List<Citation> citations
    ) {}

    public record InteractionSummary(
            UUID id,
            String question,
            String answer,
            String model,
            Instant createdAt
    ) {}
}
