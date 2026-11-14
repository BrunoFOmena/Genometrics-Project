package com.ngs.analytics.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP over stdio (profile {@code mcp-stdio}, started by scripts/mcp-stdio.sh): one JSON-RPC message per line on
 * stdin, one reply per line on stdout. Delegates to {@link McpController}, so both transports share one implementation.
 * The process is local and trusted, so it runs as the dev user; stdout carries only protocol messages.
 */
@Component
@Profile("mcp-stdio")
public class McpStdioRunner implements CommandLineRunner {

    private final McpController controller;
    private final ObjectMapper objectMapper;
    private final UserAccountRepository userRepository;
    private final NgsProperties properties;

    public McpStdioRunner(
            McpController controller,
            ObjectMapper objectMapper,
            UserAccountRepository userRepository,
            NgsProperties properties
    ) {
        this.controller = controller;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws IOException {
        PrintStream protocolOut = System.out;
        System.setOut(System.err);
        serve(System.in, protocolOut);
    }

    void serve(InputStream in, PrintStream out) throws IOException {
        var user = userRepository.findByEmailIgnoreCase(properties.getAuth().getDevEmail())
                .orElseThrow(() -> new IllegalStateException("Dev user missing; is ngs.auth.disabled true?"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    handleLine(line, out);
                }
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void handleLine(String line, PrintStream out) throws JsonProcessingException {
        Object reply;
        try {
            JsonNode request = objectMapper.readTree(line);
            reply = controller.handle(request).getBody();
        } catch (JsonProcessingException ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("jsonrpc", "2.0");
            error.put("id", null);
            error.put("error", Map.of("code", -32700, "message", "Parse error"));
            reply = error;
        }
        if (reply != null) {
            out.print(objectMapper.writeValueAsString(reply) + "\n");
            out.flush();
        }
    }
}
