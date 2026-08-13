package com.opsera.pipelineassistant.audit;

import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import com.opsera.pipelineassistant.security.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Centralized audit event recorder. Resolves the authenticated actor and client IP
 * from Spring Security context and the HTTP request, then persists an immutable
 * AuditLog record in an isolated transaction so audit writes survive outer rollbacks.
 *
 * Callers must never be affected by audit failures: the entire persistence path is
 * wrapped in a try-catch that logs errors at ERROR level without re-throwing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    static final String SYSTEM_ACTOR = "SYSTEM";

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String MFA_VERIFY_SUCCESS = "MFA_VERIFY_SUCCESS";
    public static final String MFA_VERIFY_FAILURE = "MFA_VERIFY_FAILURE";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTRATION = "REGISTRATION";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_SESSION = "SESSION";

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit event with automatic actor and IP resolution.
     *
     * @param action       what happened (e.g. "CREATE", "UPDATE", "LOGIN")
     * @param resourceType the type of resource affected (e.g. "ErrorKnowledgeBase")
     * @param resourceId   optional identifier of the specific resource (may be null)
     * @param details      optional before/after state or supplemental metadata (may be null)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String resourceType, String resourceId, Map<String, Object> details) {
        try {
            String actorEmail = resolveActorEmail();
            String ipAddress = resolveClientIp();

            AuditLog auditLog = AuditLog.builder()
                    .actorEmail(actorEmail)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit event persisted: action={}, resourceType={}, resourceId={}, actor={}",
                    action, resourceType, resourceId, actorEmail);
        } catch (Exception e) {
            log.error("Failed to persist audit event: action={}, resourceType={}, resourceId={}, error={}",
                    action, resourceType, resourceId, e.getMessage(), e);
        }
    }

    /** Convenience wrapper for CREATE events. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(String resourceType, String resourceId, Map<String, Object> details) {
        logEvent("CREATE", resourceType, resourceId, details);
    }

    /** Convenience wrapper for UPDATE events. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUpdate(String resourceType, String resourceId, Map<String, Object> details) {
        logEvent("UPDATE", resourceType, resourceId, details);
    }

    /** Convenience wrapper for DELETE events. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDelete(String resourceType, String resourceId, Map<String, Object> details) {
        logEvent("DELETE", resourceType, resourceId, details);
    }

    /**
     * Extracts the email of the authenticated user from Spring's SecurityContext.
     * Returns {@value #SYSTEM_ACTOR} for unauthenticated (null, not-authenticated,
     * or anonymous) contexts such as scheduled tasks or startup events.
     */
    private String resolveActorEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }

    /**
     * Extracts the client IP address from the current HTTP request.
     * Checks {@code X-Forwarded-For} first (supporting Nginx / load-balancer proxies),
     * taking only the first entry from a comma-separated chain.
     * Falls back to {@link HttpServletRequest#getRemoteAddr()}.
     * Returns {@code null} when invoked outside an HTTP request context.
     */
    private String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return IpAddressUtil.extractClientIp(servletAttrs.getRequest());
    }
}
