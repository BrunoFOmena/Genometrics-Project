package com.ngs.analytics.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.ai.AiAnalystService;
import com.ngs.analytics.ai.AiDtos;
import com.ngs.analytics.analytics.AnalyticsDtos;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.AnalysisService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.auth.SecurityUtils;
import com.ngs.analytics.domain.MetricFactRepository;
import com.ngs.analytics.domain.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp")
public class McpController {

    /**
     * Protocol versions this server can speak, newest first. The tool shapes used here are compatible across them;
     * older clients ignore structuredContent and outputSchema.
     */
    static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private static final Set<String> TOOL_NAMES = Set.of(
            "list_metrics", "query_metrics", "explain_qc", "get_analysis_context", "analyze_file");

    private final ObjectMapper objectMapper;
    private final MetricCatalog catalog;
    private final AnalyticsQueryService queryService;
    private final AiAnalystService analystService;
    private final AnalysisService analysisService;
    private final MetricFactRepository metricFactRepository;
    private final LocalFileAnalyzer localFileAnalyzer;

    public McpController(
            ObjectMapper objectMapper,
            MetricCatalog catalog,
            AnalyticsQueryService queryService,
            AiAnalystService analystService,
            AnalysisService analysisService,
            MetricFactRepository metricFactRepository,
            LocalFileAnalyzer localFileAnalyzer
    ) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.queryService = queryService;
        this.analystService = analystService;
        this.analysisService = analysisService;
        this.metricFactRepository = metricFactRepository;
        this.localFileAnalyzer = localFileAnalyzer;
    }

    @PostMapping
    public ResponseEntity<Object> handle(@RequestBody JsonNode request) {
        Object id = request.has("id") ? objectMapper.convertValue(request.get("id"), Object.class) : null;
        if (!request.isObject()
                || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            return ResponseEntity.ok(error(id, -32600, "Invalid JSON-RPC request"));
        }
        String method = request.path("method").asText();
        if (!request.has("id")) {
            return ResponseEntity.accepted().build();
        }
        try {
            Object result = switch (method) {
                case "initialize" -> Map.of(
                        "protocolVersion", negotiate(request.path("params").path("protocolVersion").asText(null)),
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "genometrics", "version", "1.0.0")
                );
                case "ping" -> Map.of(); // liveness check: the spec requires an empty result
                case "tools/list" -> Map.of("tools", tools());
                case "tools/call" -> callTool(request.path("params"));
                default -> throw new MethodNotFoundException(method);
            };
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (MethodNotFoundException ex) {
            return ResponseEntity.ok(error(id, -32601, ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(error(id, -32602, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.ok(error(id, -32603, "Internal MCP error"));
        }
    }

    /** Echo the client's version when supported, otherwise offer the newest one; the client decides whether to continue. */
    static String negotiate(String requested) {
        return requested != null && SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : SUPPORTED_PROTOCOL_VERSIONS.get(0);
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private Object callTool(JsonNode params) {
        UserAccount user = SecurityUtils.currentUser();
        String name = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        if (!TOOL_NAMES.contains(name)) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        try {
            Object value = switch (name) {
                case "list_metrics" -> catalog.all();
                case "query_metrics" -> queryService.query(
                        objectMapper.convertValue(arguments, AnalyticsDtos.QueryRequest.class), user.getId());
                case "explain_qc" -> analystService.ask(
                        objectMapper.convertValue(arguments, AiDtos.AskRequest.class), user);
                case "get_analysis_context" -> analysisContext(
                        UUID.fromString(arguments.path("analysisId").asText()), user);
                case "analyze_file" -> localFileAnalyzer.analyze(
                        objectMapper.convertValue(arguments.path("paths"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}),
                        arguments.path("sampleName").asText(null),
                        arguments.hasNonNull("projectId") ? UUID.fromString(arguments.path("projectId").asText()) : null,
                        user);
                default -> throw new IllegalStateException("Unreachable tool");
            };
            // structuredContent must be a JSON object; list_metrics returns a list, so it is wrapped.
            Object structured = objectMapper.convertValue(value, Object.class);
            if (structured instanceof List<?> list) {
                structured = Map.of("metrics", list);
            }
            // The spec asks for the same JSON as text too, for clients that ignore structuredContent.
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", writeJson(structured))),
                    "structuredContent", structured
            );
        } catch (Exception ex) {
            return Map.of(
                    "isError", true,
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", ex.getMessage() == null ? "Tool execution failed" : ex.getMessage()
                    ))
            );
        }
    }

    private Map<String, Object> analysisContext(UUID analysisId, UserAccount user) {
        var analysis = analysisService.get(analysisId, user.getId());
        return Map.of(
                "analysisId", analysis.getId(),
                "sampleId", analysis.getSample().getId(),
                "sample", analysis.getSample().getName(),
                "status", analysis.getStatus().name(),
                "metrics", metricFactRepository.findByAnalysisId(analysisId).stream().map(f -> Map.of(
                        "key", f.getMetricKey(),
                        "value", f.getNumericValue(),
                        "unit", f.getUnit()
                )).toList()
        );
    }

    /** Pure reads: same arguments, same answer, nothing changes. */
    private static final Map<String, Object> READ_ONLY = Map.of(
            "readOnlyHint", true, "idempotentHint", true, "destructiveHint", false, "openWorldHint", false);
    /** Records something new (history, uploads, analyses) but never deletes or overwrites. */
    private static final Map<String, Object> ADDITIVE = Map.of(
            "readOnlyHint", false, "idempotentHint", false, "destructiveHint", false, "openWorldHint", false);

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>(baseTools());
        if (localFileAnalyzer.enabled()) {
            tools.add(tool("analyze_file",
                    "Analyze local FASTQ, VCF or FASTA files by absolute path (only inside NGS_MCP_ANALYZE_ROOTS). "
                            + "Use it when the user points at a file on this machine. Pass the R1 and R2 FASTQ of one sample "
                            + "together for paired-end; R1 also gets its own single-end analysis. Returns the analysis ids "
                            + "with status QUEUED: poll get_analysis_context until status is DONE, then use query_metrics or "
                            + "explain_qc. Example: {\"paths\": [\"/data/runs/s1_R1.fastq.gz\", \"/data/runs/s1_R2.fastq.gz\"]}",
                    object(List.of("paths"), Map.of(
                            "paths", described(Map.of("type", "array", "items", type("string"), "minItems", 1, "maxItems", 2),
                                    "Absolute paths: one file, or the R1 and R2 FASTQ of one sample"),
                            "sampleName", described(Map.of("type", "string", "maxLength", 200), "Defaults to the first file name"),
                            "projectId", described(uuid(), "Existing project; defaults to the \"Agent analyses\" project"))),
                    object(List.of("projectId", "sampleId", "analyses"), Map.of(
                            "projectId", type("string"),
                            "sampleId", type("string"),
                            "analyses", arrayOf(object(List.of("analysisId", "file", "status", "paired"), Map.of(
                                    "analysisId", type("string"),
                                    "file", type("string"),
                                    "status", type("string"),
                                    "paired", type("boolean")))))),
                    ADDITIVE));
        }
        return tools;
    }

    private List<Map<String, Object>> baseTools() {
        List<String> metricKeys = catalog.all().stream().map(MetricCatalog.Definition::key).toList();
        return List.of(
                tool("list_metrics",
                        "List every metric GENOMETRICS stores, with its unit, description, domain (FASTQ or VCF) and "
                                + "allowed aggregations. Call it to learn the metric keys before query_metrics or explain_qc.",
                        object(List.of(), Map.of()),
                        object(List.of("metrics"), Map.of(
                                "metrics", arrayOf(object(List.of("key", "label", "unit"), Map.of(
                                        "key", type("string"),
                                        "label", type("string"),
                                        "domain", type("string"),
                                        "unit", type("string"),
                                        "description", type("string"),
                                        "aggregations", arrayOf(type("string")),
                                        "version", type("integer")))))),
                        READ_ONLY),
                tool("query_metrics",
                        "Query stored metric facts, one row per fact by default, with sample, analysis and unit. Filter by "
                                + "project, samples, status or date; group with dimensions and aggregate. To compare samples use "
                                + "dimensions [\"sample\", \"metric\"] with an aggregation. Example: {\"metrics\": [\"mean_quality\", "
                                + "\"gc_content\"], \"dimensions\": [\"sample\", \"metric\"], \"aggregation\": \"AVG\"}",
                        querySchema(metricKeys),
                        object(List.of("columns", "rows", "total", "truncated"), Map.of(
                                "columns", arrayOf(type("string")),
                                "rows", arrayOf(type("object")),
                                "total", type("integer"),
                                "truncated", type("boolean"))),
                        READ_ONLY),
                tool("explain_qc",
                        "Answer a quality-control question from stored facts only, with citations. Returns a readable "
                                + "summary (per analysis: FASTQ QC verdict PASS/WARN/FAIL, metric values, failing checks, and "
                                + "requested metrics that have no data) and the facts behind it. Records the question in the "
                                + "history. Example: {\"question\": \"Is this run good enough to align?\", \"metrics\": [\"mean_quality\"]}",
                        object(List.of("question"), Map.of(
                                "question", described(type("string"), "The question in plain language"),
                                "projectId", described(uuid(), "Limit to one project"),
                                "sampleIds", described(arrayOf(uuid()), "Limit to these samples"),
                                "metrics", described(arrayOf(Map.of("type", "string", "enum", metricKeys)),
                                        "Metrics to use; when omitted they are picked from the question"))),
                        object(List.of("answer", "model", "citations"), Map.of(
                                "interactionId", type("string"),
                                "answer", type("string"),
                                "model", type("string"),
                                "tools", arrayOf(type("string")),
                                "citations", arrayOf(object(List.of("analysisId", "sampleId", "metric", "unit"), Map.of(
                                        "analysisId", type("string"),
                                        "sampleId", type("string"),
                                        "metric", type("string"),
                                        "value", Map.of("type", List.of("number", "null")),
                                        "unit", type("string")))))),
                        ADDITIVE),
                tool("get_analysis_context",
                        "Get one analysis: its sample, status (QUEUED, RUNNING, DONE or FAILED) and every metric fact. Use it "
                                + "to poll an analysis until DONE or to read all metrics of one run. Example: {\"analysisId\": "
                                + "\"<uuid from query_metrics or analyze_file>\"}",
                        object(List.of("analysisId"), Map.of("analysisId", uuid())),
                        object(List.of("analysisId", "sampleId", "sample", "status", "metrics"), Map.of(
                                "analysisId", type("string"),
                                "sampleId", type("string"),
                                "sample", type("string"),
                                "status", type("string"),
                                "metrics", arrayOf(object(List.of("key", "unit"), Map.of(
                                        "key", type("string"),
                                        "value", Map.of("type", List.of("number", "null")),
                                        "unit", type("string")))))),
                        READ_ONLY)
        );
    }

    private Map<String, Object> querySchema(List<String> metricKeys) {
        List<String> statuses = Arrays.stream(com.ngs.analytics.domain.AnalysisStatus.values()).map(Enum::name).toList();
        return object(List.of("metrics"), Map.of(
                "metrics", described(Map.of("type", "array", "minItems", 1, "items", Map.of("type", "string", "enum", metricKeys)),
                        "Metric keys (see list_metrics)"),
                "dimensions", described(arrayOf(Map.of("type", "string", "enum", AnalyticsQueryService.DIMENSIONS)),
                        "Group rows by these; include \"metric\" when aggregating several metrics"),
                "aggregation", described(Map.of("type", "string", "enum", AnalyticsQueryService.AGGREGATIONS),
                        "NONE (default) returns one row per fact"),
                "filters", described(object(List.of(), Map.of(
                        "projectId", uuid(),
                        "sampleIds", arrayOf(uuid()),
                        "statuses", arrayOf(Map.of("type", "string", "enum", statuses)),
                        "from", described(Map.of("type", "string", "format", "date-time"), "Analyses created at or after"),
                        "to", described(Map.of("type", "string", "format", "date-time"), "Analyses created at or before"))),
                        "All optional"),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 1000, "description", "Default 200")
        ));
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> schema,
                                     Map<String, Object> outputSchema, Map<String, Object> annotations) {
        return Map.of("name", name, "description", description, "inputSchema", schema, "outputSchema", outputSchema,
                "annotations", annotations);
    }

    private static Map<String, Object> described(Map<String, Object> schema, String description) {
        Map<String, Object> out = new LinkedHashMap<>(schema);
        out.put("description", description);
        return out;
    }

    private static Map<String, Object> uuid() {
        return Map.of("type", "string", "format", "uuid");
    }

    private static Map<String, Object> object(List<String> required, Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static Map<String, Object> type(String name) {
        return Map.of("type", name);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize tool result", ex);
        }
    }

    private static final class MethodNotFoundException extends RuntimeException {
        private MethodNotFoundException(String method) {
            super("Method not supported: " + method);
        }
    }
}
