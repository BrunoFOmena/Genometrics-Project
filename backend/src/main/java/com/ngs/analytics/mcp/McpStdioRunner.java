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
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        // Explicit UTF-8: System.out would use the locale charset (e.g. ASCII under LANG=C) and corrupt non-ASCII names.
        PrintStream protocolOut = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
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
                    if (out.checkError()) {
                        return; // the client closed our stdout
                    }
                }
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void handleLine(String line, PrintStream out) {
        Object reply;
        try {
            JsonNode request = objectMapper.readTree(line);
            reply = controller.handle(request).getBody();
            if (reply != null) {
                reply = objectMapper.writeValueAsString(reply);
            }
        } catch (JsonProcessingException ex) {
            reply = error(-32700, "Parse error");
        } catch (RuntimeException ex) {
            reply = error(-32603, "Internal MCP error");
        }
        if (reply != null) {
            out.print(reply + "\n");
            out.flush();
        }
    }

    private String error(int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}";
    }
}
