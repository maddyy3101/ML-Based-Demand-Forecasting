package com.demandforecast.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RequestGuardFilter extends OncePerRequestFilter {

    @Value("${security.api-key.enabled:false}")
    private boolean apiKeyEnabled;

    @Value("${security.api-key.header:X-API-Key}")
    private String apiKeyHeader;

    @Value("${security.api-key.values:}")
    private String apiKeyValues;

    @Value("${security.rate-limit.enabled:false}")
    private boolean rateLimitEnabled;

    @Value("${security.rate-limit.requests-per-minute:120}")
    private int requestsPerMinute;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        response.setHeader("X-Request-ID", requestId);

        String apiKey = request.getHeader(apiKeyHeader);
        if (apiKeyEnabled && !isValidApiKey(apiKey)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "Missing or invalid API key", request.getRequestURI(), requestId);
            return;
        }

        if (rateLimitEnabled && !allowRequest(resolveClientKey(request, apiKey))) {
            writeError(response, 429,
                    "Too Many Requests", "Rate limit exceeded", request.getRequestURI(), requestId);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidApiKey(String provided) {
        if (provided == null || provided.isBlank()) {
            return false;
        }
        Set<String> allowed = Arrays.stream(apiKeyValues.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
        return !allowed.isEmpty() && allowed.contains(provided);
    }

    private String resolveClientKey(HttpServletRequest request, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean allowRequest(String clientKey) {
        long window = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = counters.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.window != window) {
                return new WindowCounter(window, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> e.getValue().window < window - 2);
        }
        return counter.count.get() <= requestsPerMinute;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String existing = request.getHeader("X-Request-ID");
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }

    private void writeError(HttpServletResponse response, int status, String error, String message,
                            String path, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "status", status,
                "error", error,
                "message", message,
                "path", path,
                "requestId", requestId,
                "timestamp", Instant.now().toString()
        );
        mapper.writeValue(response.getWriter(), body);
        log.warn("{} | status={} | path={} | requestId={}", message, status, path, requestId);
    }

    private static final class WindowCounter {
        private final long window;
        private final AtomicInteger count;

        private WindowCounter(long window, AtomicInteger count) {
            this.window = window;
            this.count = count;
        }
    }
}
