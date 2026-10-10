package com.ngs.analytics.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.ai.AiDtos.*;
import com.ngs.analytics.analytics.AnalyticsDtos;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.domain.AiInteraction;
import com.ngs.analytics.domain.AiInteractionRepository;
import com.ngs.analytics.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiAnalystService {

    private static final List<String> DEFAULT_METRICS =
            List.of("mean_quality", "gc_content", "duplication_rate", "variant_count", "mean_depth");

    private final AnalyticsQueryService queryService;
    private final MetricCatalog catalog;
    private final LlmClient llmClient;
    private final AiInteractionRepository interactionRepository;
    private final ObjectMapper objectMapper;

    public AiAnalystService(
            AnalyticsQueryService queryService,
            MetricCatalog catalog,
            LlmClient llmClient,
            AiInteractionRepository interactionRepository,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.catalog = catalog;
        this.llmClient = llmClient;
        this.interactionRepository = interactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AskResponse ask(AskRequest request, UserAccount owner) {
        List<String> metrics = selectMetrics(request);
        AnalyticsDtos.QueryRequest query = new AnalyticsDtos.QueryRequest(
                metrics,
                List.of(),
                new AnalyticsDtos.Filters(request.projectId(), request.sampleIds(), List.of("DONE"), null, null),
                "NONE",
                100
        );
        AnalyticsDtos.QueryResponse data = queryService.query(query, owner.getId());
        List<Citation> citations = citations(data.rows());
        String knowledge = metrics.stream()
                .map(catalog::require)
                .map(d -> d.key() + ": " + d.description() + " [" + d.unit() + "]")
                .collect(Collectors.joining("\n"))
                + "\n" + String.join("\n", catalog.qcKnowledge());
        String context = toJson(data.rows());
        String answer = data.rows().isEmpty()
                ? "The requested data is not available for the selected context."
                : generateAnswer(request.question(), knowledge, context);

        AiInteraction interaction = new AiInteraction();
        interaction.setOwner(owner);
        interaction.setQuestion(request.question());
        interaction.setAnswer(answer);
        interaction.setModel(llmClient.enabled() ? llmClient.model() : "deterministic");
        interaction.setToolTrace(toJson(List.of("query_metrics")));
        interaction.setCitations(toJson(citations));
        interactionRepository.save(interaction);

        return new AskResponse(
                interaction.getId(),
                answer,
                interaction.getModel(),
                List.of("query_metrics"),
                citations
        );
    }

    @Transactional(readOnly = true)
    public List<InteractionSummary> history(UUID ownerId) {
        return interactionRepository.findTop20ByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(i -> new InteractionSummary(
                        i.getId(), i.getQuestion(), i.getAnswer(), i.getModel(), i.getCreatedAt()))
                .toList();
    }

    private String generateAnswer(String question, String knowledge, String context) {
        if (!llmClient.enabled()) {
            return "Local AI is disabled. Grounded facts: " + context;
        }
        String system = """
                You are the Genometrics analyst. Answer only from METRIC DEFINITIONS and FACTS.
                Never calculate or invent missing values. If evidence is insufficient, say the data is unavailable.
                Keep the answer concise and identify the sample and metric behind every numeric claim.
                """;
        String prompt = "QUESTION:\n" + question
                + "\n\nMETRIC DEFINITIONS:\n" + knowledge
                + "\n\nFACTS:\n" + context;
        return llmClient.complete(system, prompt);
    }

    private List<String> selectMetrics(AskRequest request) {
        if (request.metrics() != null && !request.metrics().isEmpty()) {
            return request.metrics().stream().distinct().toList();
        }
        String question = request.question().toLowerCase(Locale.ROOT);
        List<String> matches = catalog.all().stream()
                .filter(d -> question.contains(d.key().replace('_', ' '))
                        || question.contains(d.label().toLowerCase(Locale.ROOT)))
                .map(MetricCatalog.Definition::key)
                .toList();
        return matches.isEmpty() ? DEFAULT_METRICS : matches;
    }

    private List<Citation> citations(List<Map<String, Object>> rows) {
        return rows.stream().limit(20).map(row -> new Citation(
                asUuid(row.get("analysisId")),
                asUuid(row.get("sampleId")),
                String.valueOf(row.get("metric")),
                row.get("value") instanceof Number n ? n.doubleValue() : null,
                String.valueOf(row.get("unit"))
        )).toList();
    }

    private UUID asUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize AI context", ex);
        }
    }
}
