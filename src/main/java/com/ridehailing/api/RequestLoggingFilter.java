package com.ridehailing.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tags every log line of a request with a request id (MDC key {@value #MDC_KEY}), echoes it in the
 * {@value #HEADER} response header, and writes one access-log line per request.
 *
 * <p>A caller-supplied id is reused only if it is short and alphanumeric, so it cannot inject
 * fake lines into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdFrom(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (isDocumentation(request)) {
                log.debug("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            } else {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private static String requestIdFrom(HttpServletRequest request) {
        String supplied = request.getHeader(HEADER);
        return supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
    }

    /** Swagger UI loads many assets; keep them out of the INFO access log. */
    private static boolean isDocumentation(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }
}
