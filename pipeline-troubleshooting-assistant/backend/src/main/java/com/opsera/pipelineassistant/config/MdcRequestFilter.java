package com.opsera.pipelineassistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enriches the MDC with request-scoped context fields (request_id, actor_email, client_ip)
 * so every log statement in the request thread includes them automatically.
 *
 * Runs after Spring Security's DelegatingFilterProxy (order -100) so that actor_email
 * reflects the authenticated principal once JWT authentication is wired in.
 *
 * MDC is cleared in a finally block to prevent context leakage across thread-pool reuse.
 */
@Component
@Order(1)
public class MdcRequestFilter extends OncePerRequestFilter {

    static final String MDC_REQUEST_ID  = "request_id";
    static final String MDC_ACTOR_EMAIL = "actor_email";
    static final String MDC_CLIENT_IP   = "client_ip";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(MDC_REQUEST_ID,  UUID.randomUUID().toString());
            MDC.put(MDC_ACTOR_EMAIL, extractActorEmail());
            MDC.put(MDC_CLIENT_IP,   extractClientIp(request));
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_ACTOR_EMAIL);
            MDC.remove(MDC_CLIENT_IP);
        }
    }

    private String extractActorEmail() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContextHolder unavailable — treat as anonymous
        }
        return "anonymous";
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; the leftmost entry is the originating client
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
