package com.atm.gateway.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One concise line per HTTP request so the phone-to-gateway link is visible
 * while the stack runs.
 *
 * <p>Without this, a successful call leaves no trace in the log: Spring only
 * narrates its own startup and first dispatch. Emitting
 * {@code clientIp METHOD path -> status (N ms)} makes it obvious whether the
 * device is talking to the gateway at all and how long each call takes.
 *
 * <p>Ordered ahead of authentication so unauthorized attempts are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("gateway.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();
            String client = clientLabel(request);
            String line = String.format("%-15s %-4s %-38s -> %3d  %4d ms",
                    client, request.getMethod(), request.getRequestURI(), status, millis);
            if (status >= 500) {
                log.error(line);
            } else if (status >= 400) {
                log.warn(line);
            } else {
                log.info(line);
            }
        }
    }

    /** Remote address without the reverse-proxy noise that {@code X-Forwarded-For} adds. */
    private String clientLabel(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "-" : remote;
    }
}
