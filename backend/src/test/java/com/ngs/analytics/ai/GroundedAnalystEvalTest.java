package com.ngs.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.analytics.AnalyticsDtos;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.AiInteractionRepository;
import com.ngs.analytics.domain.QcCheckFact;
import com.ngs.analytics.domain.QcCheckFactRepository;
import com.ngs.analytics.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
    private final QcCheckFactRepository qcChecks = mock(QcCheckFactRepository.class);
    private final AiAnalystService service = new AiAnalystService(
            queryService, new MetricCatalog(), llmClient, interactions, new ObjectMapper(), qcChecks);

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
                "The requested data is not available for the selected context (variant_count).");
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

        assertThat(response.answer()).contains("Mean quality: 31.5 Phred").doesNotContain("variant_count")
                .doesNotContain("{").doesNotContain("Grounded facts");
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.analysisId()).isEqualTo(analysisId));
    }

    @Test
    void fallsBackToGroundedFactsWhenLocalModelIsUnavailable() {
        UserAccount owner = owner();
        UUID analysisId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "analysisId", analysisId,
                "sampleId", UUID.randomUUID(),
                "metric", "mean_quality",
                "value", 31.5,
                "unit", "Phred"
        );
        when(queryService.query(any(), eq(owner.getId())))
                .thenReturn(new AnalyticsDtos.QueryResponse(List.of(), List.of(row), 1, false));
        when(llmClient.enabled()).thenReturn(true);
        when(llmClient.model()).thenReturn("llama3.2");
        when(llmClient.complete(any(), any()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Local AI is unavailable."));

        AiDtos.AskResponse response = service.ask(
                new AiDtos.AskRequest("What is the mean quality?", null, List.of(), List.of("mean_quality")),
                owner
        );

        assertThat(response.model()).isEqualTo("deterministic");
        assertThat(response.answer()).startsWith("Local AI is unavailable").contains("Mean quality: 31.5 Phred");
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.analysisId()).isEqualTo(analysisId));
    }

    @Test
    void summarizesQcVerdictAndNamesMissingMetrics() {
        UserAccount owner = owner();
        UUID analysisId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "analysisId", analysisId,
                "sampleId", UUID.randomUUID(),
                "sample", "S1",
                "fileType", "FASTQ",
                "metric", "gc_content",
                "value", 38.66943866943867,
                "unit", "%"
        );
        when(queryService.query(any(), eq(owner.getId())))
                .thenReturn(new AnalyticsDtos.QueryResponse(List.of(), List.of(row), 1, false));
        when(qcChecks.findByAnalysisId(analysisId)).thenReturn(List.of(
                check("overall", "WARN", "Overall FASTQ quality control"),
                check("gcContent", "WARN", "GC 38.7% outside 40–60%"),
                check("nContent", "PASS", "N content 0.83% is low")));

        AiDtos.AskResponse response = service.ask(
                new AiDtos.AskRequest("GC and variants?", null, List.of(), List.of("gc_content", "variant_count")),
                owner
        );

        assertThat(response.answer())
                .contains("Sample S1 (analysis " + analysisId.toString().substring(0, 8) + ", FASTQ): QC WARN")
                .contains("- GC content: 38.67%")
                .contains("- QC WARN: GC 38.7% outside 40–60%")
                .doesNotContain("N content 0.83% is low")
                .contains("Not available for the selected context: variant_count");
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.metric()).isEqualTo("gc_content"));
    }

    private static QcCheckFact check(String key, String status, String message) {
        QcCheckFact fact = new QcCheckFact();
        fact.setCheckKey(key);
        fact.setStatus(status);
        fact.setMessage(message);
        return fact;
    }

    private UserAccount owner() {
        UserAccount owner = new UserAccount();
        owner.setId(UUID.randomUUID());
        return owner;
    }
}
