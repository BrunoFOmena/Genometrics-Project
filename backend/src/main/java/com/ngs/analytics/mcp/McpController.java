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
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "genometrics", "version", "1.0.0")
                );
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

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>(baseTools());
        if (localFileAnalyzer.enabled()) {
            tools.add(tool("analyze_file", "Analyze local FASTQ/VCF files (absolute paths inside NGS_MCP_ANALYZE_ROOTS). "
                    + "Pass R1 and R2 together for paired-end. Returns analysis ids with status QUEUED; poll "
                    + "get_analysis_context until status is DONE, then query the metrics", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "paths", Map.of("type", "array", "items", type("string"), "minItems", 1, "maxItems", 2),
                            "sampleName", type("string"),
                            "projectId", type("string")),
                    "required", List.of("paths")
            ), object(List.of("projectId", "sampleId", "analyses"), Map.of(
                    "projectId", type("string"),
                    "sampleId", type("string"),
                    "analyses", arrayOf(object(List.of("analysisId", "file", "status", "paired"), Map.of(
                            "analysisId", type("string"),
                            "file", type("string"),
                            "status", type("string"),
                            "paired", type("boolean"))))))));
        }
        return tools;
    }

    private List<Map<String, Object>> baseTools() {
        return List.of(
                tool("list_metrics", "List metric definitions", Map.of("type", "object"), object(List.of("metrics"), Map.of(
                        "metrics", arrayOf(object(List.of("key", "label", "unit"), Map.of(
                                "key", type("string"),
                                "label", type("string"),
                                "domain", type("string"),
                                "unit", type("string"),
                                "description", type("string"),
                                "aggregations", arrayOf(type("string")),
                                "version", type("integer"))))))),
                tool("query_metrics", "Query grounded metric facts. To compare samples, group by "
                        + "dimensions [\"sample\", \"metric\"] with an aggregation such as AVG", querySchema(),
                        object(List.of("columns", "rows", "total", "truncated"), Map.of(
                                "columns", arrayOf(type("string")),
                                "rows", arrayOf(type("object")),
                                "total", type("integer"),
                                "truncated", type("boolean")))),
                tool("explain_qc", "Explain quality metrics with citations", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of("type", "string"),
                                "projectId", Map.of("type", "string"),
                                "sampleIds", Map.of("type", "array", "items", Map.of("type", "string")),
                                "metrics", Map.of("type", "array", "items", Map.of("type", "string"))
                        ),
                        "required", List.of("question")
                ), object(List.of("answer", "model", "citations"), Map.of(
                        "interactionId", type("string"),
                        "answer", type("string"),
                        "model", type("string"),
                        "tools", arrayOf(type("string")),
                        "citations", arrayOf(object(List.of("analysisId", "sampleId", "metric", "unit"), Map.of(
                                "analysisId", type("string"),
                                "sampleId", type("string"),
                                "metric", type("string"),
                                "value", Map.of("type", List.of("number", "null")),
                                "unit", type("string"))))))),
                tool("get_analysis_context", "Get one analysis and its metric facts", Map.of(
                        "type", "object",
                        "properties", Map.of("analysisId", Map.of("type", "string")),
                        "required", List.of("analysisId")
                ), object(List.of("analysisId", "sampleId", "sample", "status", "metrics"), Map.of(
                        "analysisId", type("string"),
                        "sampleId", type("string"),
                        "sample", type("string"),
                        "status", type("string"),
                        "metrics", arrayOf(object(List.of("key", "unit"), Map.of(
                                "key", type("string"),
                                "value", Map.of("type", List.of("number", "null")),
                                "unit", type("string")))))))
        );
    }

    private Map<String, Object> querySchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "metrics", Map.of("type", "array", "items", Map.of("type", "string")),
                        "dimensions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "filters", Map.of("type", "object"),
                        "aggregation", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "maximum", 1000)
                ),
                "required", List.of("metrics")
        );
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> schema,
                                     Map<String, Object> outputSchema) {
        return Map.of("name", name, "description", description, "inputSchema", schema, "outputSchema", outputSchema);
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
