package com.ngs.analytics.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.ai.AiAnalystService;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.AnalysisService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.domain.MetricFactRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpController controller = new McpController(
            objectMapper,
            new MetricCatalog(),
            mock(AnalyticsQueryService.class),
            mock(AiAnalystService.class),
            mock(AnalysisService.class),
            mock(MetricFactRepository.class),
            mock(LocalFileAnalyzer.class)
    );

    @Test
    @SuppressWarnings("unchecked")
    void listsReadOnlyAnalyticsTools() throws Exception {
        Map<String, Object> response = (Map<String, Object>) controller.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """)).getBody();
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");

        assertThat(tools).extracting(tool -> tool.get("name"))
                .containsExactly(
                        "list_metrics",
                        "query_metrics",
                        "explain_qc",
                        "get_analysis_context"
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyToolDeclaresAnObjectOutputSchema() throws Exception {
        Map<String, Object> response = (Map<String, Object>) controller.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                """)).getBody();
        List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) response.get("result")).get("tools");

        assertThat(tools).allSatisfy(tool -> {
            Map<String, Object> schema = (Map<String, Object>) tool.get("outputSchema");
            assertThat(schema).as(String.valueOf(tool.get("name"))).containsEntry("type", "object").containsKey("required");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsStructuredContentAndTheSameJsonAsText() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.ngs.analytics.domain.UserAccount(), null, List.of()));
        try {
            Map<String, Object> response = (Map<String, Object>) controller.handle(objectMapper.readTree("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_metrics","arguments":{}}}
                    """)).getBody();
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) structured.get("metrics");
            String text = (String) ((List<Map<String, Object>>) result.get("content")).get(0).get("text");

            assertThat(metrics).extracting(m -> m.get("key")).contains("mean_quality", "variant_count");
            assertThat(objectMapper.readTree(text)).isEqualTo(objectMapper.valueToTree(structured));
            assertThat(result).doesNotContainKey("isError");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void answersPingWithAnEmptyResult() throws Exception {
        Map<String, Object> response = (Map<String, Object>) controller.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":"p1","method":"ping"}
                """)).getBody();

        assertThat(response).containsEntry("id", "p1").containsEntry("result", Map.of()).doesNotContainKey("error");
    }

    @Test
    @SuppressWarnings("unchecked")
    void negotiatesTheProtocolVersion() throws Exception {
        String supported = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}
                """;
        String unknown = """
                {"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}
                """;
        String missing = """
                {"jsonrpc":"2.0","id":3,"method":"initialize"}
                """;

        assertThat(initializeVersion(supported)).isEqualTo("2025-03-26");
        assertThat(initializeVersion(unknown)).isEqualTo(McpController.SUPPORTED_PROTOCOL_VERSIONS.get(0));
        assertThat(initializeVersion(missing)).isEqualTo(McpController.SUPPORTED_PROTOCOL_VERSIONS.get(0));
    }

    @SuppressWarnings("unchecked")
    private String initializeVersion(String request) throws Exception {
        Map<String, Object> response = (Map<String, Object>) controller.handle(objectMapper.readTree(request)).getBody();
        return (String) ((Map<String, Object>) response.get("result")).get("protocolVersion");
    }

    @Test
    void doesNotReplyToJsonRpcNotifications() throws Exception {
        var response = controller.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
    }
}
