package com.ngs.analytics.ai;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt);
    String model();
    boolean enabled();
}
