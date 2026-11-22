package com.leonardogalvao.pontocontrole.web;

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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?=/|$)");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = validRequestIdOrNew(request.getHeader(HEADER_NAME));
        response.setHeader(HEADER_NAME, requestId);
        long startedAt = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                LOGGER.info(
                        "http_request method={} path={} status={} durationMs={} errorCode={}",
                        request.getMethod(),
                        normalizedPath(request.getRequestURI()),
                        response.getStatus(),
                        durationMillis,
                        errorCode(request));
            }
        }
    }

    private String normalizedPath(String requestUri) {
        return UUID_PATH_SEGMENT.matcher(requestUri).replaceAll("{id}");
    }

    private String errorCode(HttpServletRequest request) {
        Object errorCode = request.getAttribute(ApiErrorAttributes.ERROR_CODE_ATTRIBUTE);
        return errorCode instanceof String value ? value : "-";
    }

    private String validRequestIdOrNew(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // Cabeçalhos arbitrários não entram no MDC para evitar injeção em logs.
            }
        }
        return UUID.randomUUID().toString();
    }
}
