package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps web pages open in the user's browser away from the local API (DNS rebinding and cross-site requests).
 * <ul>
 *   <li>Auth off: the {@code Host} header must be loopback or in {@code ngs.security.allowed-hosts}. A rebinding page
 *       reaches 127.0.0.1 with its own host name, so it is rejected here.</li>
 *   <li>Always: any state-changing request (not GET/HEAD/OPTIONS/TRACE) that carries an {@code Origin} header, which
 *       covers {@code /mcp} and every {@code /api} call, must be same-host or in {@code ngs.security.allowed-origins}.
 *       Matching is by method, not path, so it fails closed. Non-browser clients send no {@code Origin}.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalRequestGuardFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final NgsProperties properties;

    public LocalRequestGuardFilter(NgsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String hostHeader = request.getHeader("Host");
        if (properties.getAuth().isDisabled() && !hostAllowed(hostHeader)) {
            reject(response, "Host not allowed");
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !SAFE_METHODS.contains(request.getMethod()) && !originAllowed(origin, hostHeader)) {
            reject(response, "Origin not allowed");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hostAllowed(String hostHeader) {
        String host = hostName(hostHeader);
        return host != null && (LOOPBACK_HOSTS.contains(host)
                || properties.getSecurity().getAllowedHosts().stream().anyMatch(h -> host.equals(hostName(h))));
    }

    private boolean originAllowed(String origin, String hostHeader) {
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        if (properties.getSecurity().getAllowedOrigins().stream()
                .anyMatch(o -> o.trim().replaceAll("/+$", "").equalsIgnoreCase(normalized))) {
            return true;
        }
        int scheme = normalized.indexOf("://");
        return scheme > 0 && hostHeader != null && normalized.substring(scheme + 3).equals(hostHeader.trim().toLowerCase(Locale.ROOT));
    }

    /** Host header without port, lower case; IPv6 literals keep their brackets. */
    private static String hostName(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end < 0 ? null : host.substring(0, end + 1);
        }
        int colon = host.lastIndexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\",\"status\":403}");
    }
}
