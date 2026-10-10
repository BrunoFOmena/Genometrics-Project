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
            mock(MetricFactRepository.class)
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
                        "compare_analyses",
                        "explain_qc",
                        "get_analysis_context"
                );
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
