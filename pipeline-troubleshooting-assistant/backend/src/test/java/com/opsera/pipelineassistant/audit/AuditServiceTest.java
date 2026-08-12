package com.opsera.pipelineassistant.audit;

import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ── Actor resolution ──────────────────────────────────────────────────────

    @Test
    void logEvent_persistsCorrectAuditLogWithAllFieldsPopulated() {
        setAuthenticatedUser("alice@example.com");
        setRequestWithIp("203.0.113.10");
        Map<String, Object> details = Map.of("before", "old-value", "after", "new-value");
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.logEvent("UPDATE", "ErrorKnowledgeBase", "42", details);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAction()).isEqualTo("UPDATE");
        assertThat(saved.getResourceType()).isEqualTo("ErrorKnowledgeBase");
        assertThat(saved.getResourceId()).isEqualTo("42");
        assertThat(saved.getDetails()).isEqualTo(details);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    void logEvent_setsSystemActorWhenSecurityContextHasNoAuthentication() {
        // no authentication set — SecurityContextHolder holds empty context

        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorEmail()).isEqualTo(AuditService.SYSTEM_ACTOR);
    }

    @Test
    void logEvent_setsSystemActorWhenAuthenticationIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        auditService.logEvent("READ", "AnalyzedLog", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorEmail()).isEqualTo(AuditService.SYSTEM_ACTOR);
    }

    @Test
    void logEvent_extractsActorEmailFromAuthenticatedPrincipal() {
        setAuthenticatedUser("admin@opsera.io");

        auditService.logEvent("DELETE", "ErrorKnowledgeBase", "99", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorEmail()).isEqualTo("admin@opsera.io");
    }

    // ── IP extraction ─────────────────────────────────────────────────────────

    @Test
    void logEvent_extractsIpFromXForwardedForHeaderSingleIp() {
        setAuthenticatedUser("user@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.5");
        request.setRemoteAddr("10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "7", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("198.51.100.5");
    }

    @Test
    void logEvent_extractsFirstIpFromCommaDelimitedXForwardedFor() {
        setAuthenticatedUser("user@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2, 172.16.0.3");
        request.setRemoteAddr("10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logEvent("UPDATE", "ErrorKnowledgeBase", "5", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.1");
    }

    @Test
    void logEvent_fallsBackToRemoteAddrWhenNoXForwardedForHeader() {
        setAuthenticatedUser("user@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logEvent("CREATE", "AnalyzedLog", "10", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("192.168.1.50");
    }

    @Test
    void logEvent_setsNullIpAddressWhenNoRequestContextExists() {
        setAuthenticatedUser("user@example.com");
        // RequestContextHolder has no attributes — simulates scheduled task / async thread

        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
    }

    // ── Details map ───────────────────────────────────────────────────────────

    @Test
    void logEvent_handlesNullDetailsMapGracefully() {
        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    void logEvent_passesDetailsMapThroughToEntityUnchanged() {
        Map<String, Object> details = Map.of("severity", "HIGH", "category", "Docker", "matchCount", 3);
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "88", details);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isEqualTo(details);
    }

    // ── Exception handling ────────────────────────────────────────────────────

    @Test
    void logEvent_catchesRepositoryExceptionWithoutPropagatingToCaller() {
        doThrow(new DataAccessResourceFailureException("simulated DB failure"))
                .when(auditLogRepository).save(any());

        assertThatCode(() ->
                auditService.logEvent("DELETE", "ErrorKnowledgeBase", "5", null))
                .doesNotThrowAnyException();
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    @Test
    void logCreate_delegatesToLogEventWithCreateAction() {
        auditService.logCreate("ErrorKnowledgeBase", "10", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("CREATE");
    }

    @Test
    void logUpdate_delegatesToLogEventWithUpdateAction() {
        auditService.logUpdate("ErrorKnowledgeBase", "10", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("UPDATE");
    }

    @Test
    void logDelete_delegatesToLogEventWithDeleteAction() {
        auditService.logDelete("ErrorKnowledgeBase", "10", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("DELETE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setAuthenticatedUser(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))));
    }

    private void setRequestWithIp(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
