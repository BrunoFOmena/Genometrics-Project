package com.ngs.analytics.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.ai.AiDtos.*;
import com.ngs.analytics.analytics.AnalyticsDtos;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.domain.AiInteraction;
import com.ngs.analytics.domain.AiInteractionRepository;
import com.ngs.analytics.domain.QcCheckFact;
import com.ngs.analytics.domain.QcCheckFactRepository;
import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiAnalystService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalystService.class);

    private static final List<String> DEFAULT_METRICS =
            List.of("mean_quality", "gc_content", "duplication_rate", "variant_count", "mean_depth");
    private static final int MAX_SUMMARY_FACTS = 40;

    private final AnalyticsQueryService queryService;
    private final MetricCatalog catalog;
    private final LlmClient llmClient;
    private final AiInteractionRepository interactionRepository;
    private final ObjectMapper objectMapper;
    private final QcCheckFactRepository qcCheckFactRepository;

    public AiAnalystService(
            AnalyticsQueryService queryService,
            MetricCatalog catalog,
            LlmClient llmClient,
            AiInteractionRepository interactionRepository,
            ObjectMapper objectMapper,
            QcCheckFactRepository qcCheckFactRepository
    ) {
        this.queryService = queryService;
        this.catalog = catalog;
        this.llmClient = llmClient;
        this.interactionRepository = interactionRepository;
        this.objectMapper = objectMapper;
        this.qcCheckFactRepository = qcCheckFactRepository;
    }

    @Transactional
    public AskResponse ask(AskRequest request, UserAccount owner) {
        List<String> metrics = selectMetrics(request);
        boolean explicitMetrics = metrics != DEFAULT_METRICS;
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
        Generated generated = data.rows().isEmpty()
                ? new Generated("The requested data is not available for the selected context ("
                        + String.join(", ", metrics) + ").", "deterministic")
                : generateAnswer(request.question(), knowledge, context,
                        summary(data.rows(), explicitMetrics ? metrics : List.of()));

        AiInteraction interaction = new AiInteraction();
        interaction.setOwner(owner);
        interaction.setQuestion(request.question());
        interaction.setAnswer(generated.answer());
        interaction.setModel(generated.model());
        interaction.setToolTrace(toJson(List.of("query_metrics")));
        interaction.setCitations(toJson(citations));
        interactionRepository.save(interaction);

        return new AskResponse(
                interaction.getId(),
                generated.answer(),
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

    private Generated generateAnswer(String question, String knowledge, String context, String summary) {
        if (!llmClient.enabled()) {
            return new Generated(summary, "deterministic");
        }
        String system = """
                You are the Genometrics analyst. Answer only from METRIC DEFINITIONS and FACTS.
                Never calculate or invent missing values. If evidence is insufficient, say the data is unavailable.
                Keep the answer concise and identify the sample and metric behind every numeric claim.
                """;
        String prompt = "QUESTION:\n" + question
                + "\n\nMETRIC DEFINITIONS:\n" + knowledge
                + "\n\nFACTS:\n" + context;
        try {
            return new Generated(llmClient.complete(system, prompt), llmClient.model());
        } catch (ApiException ex) {
            log.warn("Local AI unavailable, answering with grounded facts: {}", ex.getMessage());
            return new Generated("Local AI is unavailable; grounded summary:\n" + summary, "deterministic");
        }
    }

    private record Generated(String answer, String model) {
    }

    /**
     * Deterministic, readable answer: one block per analysis with its FASTQ QC verdict and non-passing checks, one
     * line per fact, then the requested metrics that have no fact. Citations stay in their own field.
     */
    private String summary(List<Map<String, Object>> rows, List<String> requested) {
        Map<String, List<Map<String, Object>>> byAnalysis = new LinkedHashMap<>();
        rows.stream().limit(MAX_SUMMARY_FACTS)
                .forEach(row -> byAnalysis.computeIfAbsent(String.valueOf(row.get("analysisId")), k -> new ArrayList<>()).add(row));
        StringBuilder out = new StringBuilder();
        byAnalysis.forEach((analysisId, facts) -> {
            Map<String, Object> first = facts.get(0);
            Object sample = first.get("sample") != null ? first.get("sample") : first.get("sampleId");
            out.append("Sample ").append(sample).append(" (analysis ").append(analysisId, 0, Math.min(8, analysisId.length()));
            if (first.get("fileType") != null) {
                out.append(", ").append(first.get("fileType"));
            }
            out.append(')');
            List<QcCheckFact> checks = qcCheckFactRepository.findByAnalysisId(asUuid(first.get("analysisId")));
            checks.stream().filter(c -> "overall".equals(c.getCheckKey())).findFirst()
                    .ifPresent(c -> out.append(": QC ").append(c.getStatus()));
            out.append('\n');
            for (Map<String, Object> fact : facts) {
                String key = String.valueOf(fact.get("metric"));
                String label = catalog.all().stream().filter(d -> d.key().equals(key))
                        .map(MetricCatalog.Definition::label).findFirst().orElse(key);
                out.append("- ").append(label).append(": ").append(formatValue(fact.get("value"), String.valueOf(fact.get("unit")))).append('\n');
            }
            checks.stream().filter(c -> !"overall".equals(c.getCheckKey()) && !"PASS".equals(c.getStatus()))
                    .forEach(c -> out.append("- QC ").append(c.getStatus()).append(": ").append(c.getMessage()).append('\n'));
        });
        if (rows.size() > MAX_SUMMARY_FACTS) {
            out.append("(").append(rows.size() - MAX_SUMMARY_FACTS).append(" more facts not shown; use query_metrics)\n");
        }
        Set<String> found = rows.stream().map(r -> String.valueOf(r.get("metric"))).collect(Collectors.toSet());
        List<String> missing = requested.stream().filter(m -> !found.contains(m)).toList();
        if (!missing.isEmpty()) {
            out.append("Not available for the selected context: ").append(String.join(", ", missing)).append('\n');
        }
        return out.toString().stripTrailing();
    }

    private static String formatValue(Object value, String unit) {
        String number = value instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                : String.valueOf(value);
        return "%".equals(unit) ? number + "%" : number + " " + unit;
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
