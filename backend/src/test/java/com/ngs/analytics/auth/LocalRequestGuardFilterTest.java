package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRequestGuardFilterTest {

    private final NgsProperties properties = new NgsProperties();
    private final LocalRequestGuardFilter filter = new LocalRequestGuardFilter(properties);

    private int status(String method, String uri, String host, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (host != null) {
            request.addHeader("Host", host);
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        boolean passed = chain.getRequest() != null;
        return passed ? 200 : response.getStatus();
    }

    @Test
    void acceptsLoopbackHostsWhenAuthIsOff() throws Exception {
        properties.getAuth().setDisabled(true);

        assertThat(status("GET", "/api/projects", "localhost:8080", null)).isEqualTo(200);
        assertThat(status("GET", "/api/projects", "127.0.0.1:8080", null)).isEqualTo(200);
        assertThat(status("GET", "/api/projects", "[::1]:8080", null)).isEqualTo(200);
        assertThat(status("GET", "/api/projects", "LOCALHOST", null)).isEqualTo(200);
    }

    @Test
    void rejectsRebindingHostWhenAuthIsOff() throws Exception {
        properties.getAuth().setDisabled(true);

        assertThat(status("GET", "/api/projects", "evil.example:8080", null)).isEqualTo(403);
        // a rebinding page is "same-origin" with its own host name, so the Host check is what stops it
        assertThat(status("POST", "/mcp", "evil.example", "http://evil.example")).isEqualTo(403);
        assertThat(status("GET", "/actuator/health", null, null)).isEqualTo(403);
    }

    @Test
    void acceptsConfiguredHosts() throws Exception {
        properties.getAuth().setDisabled(true);
        properties.getSecurity().setAllowedHosts(List.of("genometrics.lan"));

        assertThat(status("GET", "/api/projects", "genometrics.lan:8080", null)).isEqualTo(200);
    }

    @Test
    void doesNotCheckTheHostWhenAuthIsOn() throws Exception {
        properties.getAuth().setDisabled(false);

        assertThat(status("GET", "/api/projects", "evil.example", null)).isEqualTo(200);
    }

    @Test
    void rejectsForeignOriginOnMcpAndStateChangingApiCalls() throws Exception {
        properties.getAuth().setDisabled(true);

        assertThat(status("POST", "/mcp", "localhost:8080", "http://evil.example")).isEqualTo(403);
        assertThat(status("POST", "/mcp", "localhost:8080", "null")).isEqualTo(403);
        assertThat(status("POST", "/api/projects", "localhost:8080", "http://evil.example")).isEqualTo(403);
        assertThat(status("DELETE", "/api/projects/1", "localhost:8080", "http://evil.example")).isEqualTo(403);
    }

    @Test
    void acceptsNoOriginSameOriginAndAllowlistedOrigins() throws Exception {
        properties.getAuth().setDisabled(true);
        properties.getSecurity().setAllowedOrigins(List.of("http://localhost:3000/"));

        assertThat(status("POST", "/mcp", "localhost:8080", null)).isEqualTo(200);
        assertThat(status("POST", "/api/projects", "localhost:8080", "http://localhost:8080")).isEqualTo(200);
        assertThat(status("POST", "/mcp", "localhost:8080", "http://localhost:3000")).isEqualTo(200);
    }

    @Test
    void ignoresOriginOnReadOnlyApiCalls() throws Exception {
        properties.getAuth().setDisabled(true);

        assertThat(status("GET", "/api/projects", "localhost:8080", "http://evil.example")).isEqualTo(200);
    }

    @Test
    void checksOriginEvenWhenAuthIsOn() throws Exception {
        properties.getAuth().setDisabled(false);

        assertThat(status("POST", "/mcp", "localhost:8080", "http://evil.example")).isEqualTo(403);
    }
}
