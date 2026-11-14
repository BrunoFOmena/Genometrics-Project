package com.ngs.analytics.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.ai.AiAnalystService;
import com.ngs.analytics.analytics.AnalysisService;
import com.ngs.analytics.analytics.AnalyticsQueryService;
import com.ngs.analytics.analytics.MetricCatalog;
import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.MetricFactRepository;
import com.ngs.analytics.domain.Role;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpStdioRunnerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final McpStdioRunner runner = new McpStdioRunner(
            new McpController(
                    objectMapper,
                    new MetricCatalog(),
                    mock(AnalyticsQueryService.class),
                    mock(AiAnalystService.class),
                    mock(AnalysisService.class),
                    mock(MetricFactRepository.class),
                    mock(LocalFileAnalyzer.class)),
            objectMapper,
            users,
            new NgsProperties());

    private List<JsonNode> serve(String input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        runner.serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        List<JsonNode> replies = new java.util.ArrayList<>();
        for (String line : bytes.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isEmpty()) {
                replies.add(objectMapper.readTree(line));
            }
        }
        return replies;
    }

    private void devUserExists() {
        UserAccount user = new UserAccount();
        user.setRole(Role.USER);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
    }

    @Test
    void answersOneJsonLinePerRequestAndSkipsNotifications() throws Exception {
        devUserExists();

        List<JsonNode> replies = serve("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}

                not json
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_metrics","arguments":{}}}
                """);

        assertThat(replies).hasSize(3);
        assertThat(replies.get(0).path("id").asInt()).isEqualTo(1);
        assertThat(replies.get(0).path("result").path("serverInfo").path("name").asText()).isEqualTo("genometrics");
        assertThat(replies.get(1).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(replies.get(1).path("id").isNull()).isTrue();
        // tools/call needs an authenticated user: proves the runner runs as the dev user
        assertThat(replies.get(2).path("result").path("isError").asBoolean(false)).isFalse();
        assertThat(replies.get(2).path("result").path("content").get(0).path("text").asText()).contains("read_count");
    }

    @Test
    void echoesNonAsciiIdsAndSurvivesNonObjectInput() throws Exception {
        devUserExists();

        List<JsonNode> replies = serve("""
                {"jsonrpc":"2.0","id":"São μ","method":"tools/list"}
                [{"jsonrpc":"2.0","id":3,"method":"tools/list"}]
                5
                """);

        assertThat(replies).hasSize(3);
        assertThat(replies.get(0).path("id").asText()).isEqualTo("São μ");
        assertThat(replies.get(1).path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(replies.get(2).path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void clearsTheSecurityContextWhenInputEnds() throws Exception {
        devUserExists();

        serve("");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void failsClearlyWhenTheDevUserIsMissing() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serve("")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Dev user");
    }
}
