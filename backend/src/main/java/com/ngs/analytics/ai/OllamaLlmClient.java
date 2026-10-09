package com.ngs.analytics.ai;

import com.ngs.analytics.common.ApiException;
import com.ngs.analytics.common.NgsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OllamaLlmClient implements LlmClient {

    private final NgsProperties properties;
    private final RestClient restClient;

    public OllamaLlmClient(NgsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getAi().getBaseUrl()).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/chat")
                    .body(Map.of(
                            "model", model(),
                            "stream", false,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> message = response == null
                    ? null : (Map<String, Object>) response.get("message");
            if (message == null || message.get("content") == null) {
                throw new IllegalStateException("Ollama returned no message");
            }
            return message.get("content").toString();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Local AI is unavailable. Confirm that Ollama is running.");
        }
    }

    @Override
    public String model() {
        return properties.getAi().getModel();
    }

    @Override
    public boolean enabled() {
        return properties.getAi().isEnabled();
    }
}
