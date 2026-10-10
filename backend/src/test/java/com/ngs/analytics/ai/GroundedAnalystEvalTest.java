package com.ngs.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.analytics.AnalyticsDtos;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.domain.AiInteractionRepository;
import com.ngs.analytics.domain.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroundedAnalystEvalTest {

    private final AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AiInteractionRepository interactions = mock(AiInteractionRepository.class);
    private final AiAnalystService service = new AiAnalystService(
            queryService, new MetricCatalog(), llmClient, interactions, new ObjectMapper());

    @Test
    void refusesToInventAValueWhenNoFactExists() {
        UserAccount owner = owner();
        when(queryService.query(any(), eq(owner.getId())))
                .thenReturn(new AnalyticsDtos.QueryResponse(List.of(), List.of(), 0, false));

        AiDtos.AskResponse response = service.ask(
                new AiDtos.AskRequest("How many variants are there?", null, List.of(), List.of("variant_count")),
                owner
        );

        assertThat(response.answer()).isEqualTo(
                "The requested data is not available for the selected context.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void deterministicAnswerContainsOnlyGroundedFacts() {
        UserAccount owner = owner();
        UUID analysisId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "analysisId", analysisId,
                "sampleId", sampleId,
                "metric", "mean_quality",
                "value", 31.5,
                "unit", "Phred"
        );
        when(queryService.query(any(), eq(owner.getId())))
                .thenReturn(new AnalyticsDtos.QueryResponse(List.of(), List.of(row), 1, false));

        AiDtos.AskResponse response = service.ask(
                new AiDtos.AskRequest("What is the mean quality?", null, List.of(sampleId),
                        List.of("mean_quality")),
                owner
        );

        assertThat(response.answer()).contains("31.5").doesNotContain("variant_count");
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.analysisId()).isEqualTo(analysisId));
    }

    private UserAccount owner() {
        UserAccount owner = new UserAccount();
        owner.setId(UUID.randomUUID());
        return owner;
    }
}
