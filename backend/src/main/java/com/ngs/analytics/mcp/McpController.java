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
            "list_metrics", "query_metrics", "compare_analyses", "explain_qc", "get_analysis_context");

    private final ObjectMapper objectMapper;
    private final MetricCatalog catalog;
    private final AnalyticsQueryService queryService;
    private final AiAnalystService analystService;
    private final AnalysisService analysisService;
    private final MetricFactRepository metricFactRepository;

    public McpController(
            ObjectMapper objectMapper,
            MetricCatalog catalog,
            AnalyticsQueryService queryService,
            AiAnalystService analystService,
            AnalysisService analysisService,
            MetricFactRepository metricFactRepository
    ) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.queryService = queryService;
        this.analystService = analystService;
        this.analysisService = analysisService;
        this.metricFactRepository = metricFactRepository;
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
                case "query_metrics", "compare_analyses" -> queryService.query(
                        objectMapper.convertValue(arguments, AnalyticsDtos.QueryRequest.class), user.getId());
                case "explain_qc" -> analystService.ask(
                        objectMapper.convertValue(arguments, AiDtos.AskRequest.class), user);
                case "get_analysis_context" -> analysisContext(
                        UUID.fromString(arguments.path("analysisId").asText()), user);
                default -> throw new IllegalStateException("Unreachable tool");
            };
            return Map.of("content", List.of(Map.of(
                    "type", "text",
                    "text", writeJson(value)
            )));
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
        return List.of(
                tool("list_metrics", "List metric definitions", Map.of("type", "object")),
                tool("query_metrics", "Query grounded metric facts", querySchema()),
                tool("compare_analyses", "Compare metrics across samples", querySchema()),
                tool("explain_qc", "Explain quality metrics with citations", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of("type", "string"),
                                "projectId", Map.of("type", "string"),
                                "sampleIds", Map.of("type", "array", "items", Map.of("type", "string")),
                                "metrics", Map.of("type", "array", "items", Map.of("type", "string"))
                        ),
                        "required", List.of("question")
                )),
                tool("get_analysis_context", "Get one analysis and its metric facts", Map.of(
                        "type", "object",
                        "properties", Map.of("analysisId", Map.of("type", "string")),
                        "required", List.of("analysisId")
                ))
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

    private Map<String, Object> tool(String name, String description, Map<String, Object> schema) {
        return Map.of("name", name, "description", description, "inputSchema", schema);
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
