package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceUserTest {

    @Mock private KnowledgeBaseService knowledgeBaseService;
    @Mock private AnalyzedLogRepository analyzedLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private LogSanitizer logSanitizer;
    @Mock private PatternMatcher patternMatcher;
    @Mock private ScoringEngine scoringEngine;
    @Mock private ResponseTemplater responseTemplater;
    @Mock private MeterRegistry meterRegistry;
    @Mock private AuditService auditService;

    @InjectMocks
    private AnalysisService analysisService;

    private static final String EMAIL = "analyst@example.com";
    private static final UUID USER_ID = UUID.randomUUID();

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .displayName("Test Analyst")
                .role(Role.ANALYST)
                .build();

        lenient().when(logSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(knowledgeBaseService.getAllEntries()).thenReturn(Collections.emptyList());
        lenient().when(patternMatcher.match(anyString(), any())).thenReturn(Collections.emptyList());
        lenient().when(scoringEngine.calculateConfidence(0, 0)).thenReturn(20);
        lenient().when(responseTemplater.generateUnclassified()).thenReturn("Unclassified update");
        lenient().when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Authenticated user ─────────────────────────────────────────────────────

    @Test
    void analyze_authenticatedUser_setsUserOnSavedLog() {
        setAuthenticatedUser(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));

        analysisService.analyze("some log text");

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNotNull();
        assertThat(captor.getValue().getUser().getId()).isEqualTo(USER_ID);
    }

    @Test
    void analyze_authenticatedUser_userIdMatchesAuthenticatedEmail() {
        setAuthenticatedUser(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));

        analysisService.analyze("some log text");

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getEmail()).isEqualTo(EMAIL);
    }

    // ── Unauthenticated (no SecurityContext) ──────────────────────────────────

    @Test
    void analyze_noAuthentication_setsUserToNull() {
        SecurityContextHolder.clearContext();

        analysisService.analyze("some log text");

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNull();
    }

    @Test
    void analyze_anonymousAuthentication_setsUserToNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        analysisService.analyze("some log text");

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNull();
    }

    // ── extractCurrentUser utility ────────────────────────────────────────────

    @Test
    void extractCurrentUser_withAuthenticatedUser_returnsUser() {
        setAuthenticatedUser(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));

        User result = analysisService.extractCurrentUser();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(USER_ID);
    }

    @Test
    void extractCurrentUser_withNoAuth_returnsNull() {
        SecurityContextHolder.clearContext();

        User result = analysisService.extractCurrentUser();

        assertThat(result).isNull();
    }

    @Test
    void extractCurrentUser_withUnknownEmail_returnsNull() {
        setAuthenticatedUser("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        User result = analysisService.extractCurrentUser();

        assertThat(result).isNull();
    }

    private void setAuthenticatedUser(String email) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                org.springframework.security.core.userdetails.User.builder()
                        .username(email)
                        .password("")
                        .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))
                        .build(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
