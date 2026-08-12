package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceAuditTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private AuditService auditService;

    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseService(errorRepository, new SimpleMeterRegistry(), auditService);
    }

    private ErrorKnowledgeBase entity(Long id) {
        return ErrorKnowledgeBase.builder()
                .id(id)
                .errorPattern("oom,heap")
                .category("Memory")
                .rootCause("Heap exhausted")
                .solution("Increase Xmx")
                .severity("HIGH")
                .build();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_callsLogCreateWithCorrectResourceType() {
        ErrorKnowledgeBase input = entity(null);
        ErrorKnowledgeBase saved = entity(1L);
        when(errorRepository.save(any())).thenReturn(saved);

        service.create(input);

        verify(auditService).logCreate(eq("KNOWLEDGE_BASE"), eq("1"), any());
    }

    @Test
    void create_detailsContainCategorySeverityAndErrorPattern() {
        ErrorKnowledgeBase saved = entity(1L);
        when(errorRepository.save(any())).thenReturn(saved);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        service.create(entity(null));

        verify(auditService).logCreate(eq("KNOWLEDGE_BASE"), eq("1"), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertThat(details).containsEntry("category", "Memory");
        assertThat(details).containsEntry("severity", "HIGH");
        assertThat(details).containsEntry("errorPattern", "oom,heap");
    }

    @Test
    void create_auditExceptionDoesNotPropagateAndEntityIsReturned() {
        ErrorKnowledgeBase saved = entity(1L);
        when(errorRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("audit failure")).when(auditService).logCreate(any(), any(), any());

        ErrorKnowledgeBase result = service.create(entity(null));

        assertThat(result.getId()).isEqualTo(1L);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_callsLogUpdateWithCorrectResourceTypeAndId() {
        ErrorKnowledgeBase existing = entity(5L);
        when(errorRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(5L, entity(null));

        verify(auditService).logUpdate(eq("KNOWLEDGE_BASE"), eq("5"), any());
    }

    @Test
    void update_detailsContainBeforeAndAfterMaps() {
        ErrorKnowledgeBase existing = ErrorKnowledgeBase.builder()
                .id(5L).errorPattern("old-pattern").category("OldCat").rootCause("r").solution("s").severity("LOW").build();
        when(errorRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ErrorKnowledgeBase update = ErrorKnowledgeBase.builder()
                .errorPattern("new-pattern").category("NewCat").rootCause("r2").solution("s2").severity("HIGH").build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        service.update(5L, update);

        verify(auditService).logUpdate(eq("KNOWLEDGE_BASE"), eq("5"), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertThat(details).containsKey("before");
        assertThat(details).containsKey("after");

        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) details.get("before");
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) details.get("after");

        assertThat(before).containsEntry("category", "OldCat");
        assertThat(before).containsEntry("severity", "LOW");
        assertThat(before).containsEntry("errorPattern", "old-pattern");
        assertThat(after).containsEntry("category", "NewCat");
        assertThat(after).containsEntry("severity", "HIGH");
        assertThat(after).containsEntry("errorPattern", "new-pattern");
    }

    @Test
    void update_beforeStateIsCapturedBeforeEntityModification() {
        ErrorKnowledgeBase existing = ErrorKnowledgeBase.builder()
                .id(5L).errorPattern("original").category("OrigCat").rootCause("r").solution("s").severity("LOW").build();
        when(errorRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ErrorKnowledgeBase update = ErrorKnowledgeBase.builder()
                .errorPattern("modified").category("ModCat").rootCause("r2").solution("s2").severity("HIGH").build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        service.update(5L, update);

        verify(auditService).logUpdate(any(), any(), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) captor.getValue().get("before");
        assertThat(before).containsEntry("category", "OrigCat");
        assertThat(before).containsEntry("errorPattern", "original");
    }

    @Test
    void update_notFoundDoesNotCallAudit() {
        when(errorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                service.update(999L, entity(null)));

        verify(auditService, never()).logUpdate(any(), any(), any());
    }

    @Test
    void update_auditExceptionDoesNotPropagateAndEntityIsReturned() {
        when(errorRepository.findById(5L)).thenReturn(Optional.of(entity(5L)));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("audit failure")).when(auditService).logUpdate(any(), any(), any());

        ErrorKnowledgeBase result = service.update(5L, entity(null));

        assertThat(result.getId()).isEqualTo(5L);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsLogDeleteWithCorrectResourceTypeAndId() {
        when(errorRepository.findById(3L)).thenReturn(Optional.of(entity(3L)));

        service.delete(3L);

        verify(auditService).logDelete(eq("KNOWLEDGE_BASE"), eq("3"), any());
    }

    @Test
    void delete_detailsContainEntityState() {
        ErrorKnowledgeBase ent = ErrorKnowledgeBase.builder()
                .id(3L).errorPattern("del-pattern").category("DelCat").rootCause("r").solution("s").severity("MEDIUM").build();
        when(errorRepository.findById(3L)).thenReturn(Optional.of(ent));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        service.delete(3L);

        verify(auditService).logDelete(eq("KNOWLEDGE_BASE"), eq("3"), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertThat(details).containsEntry("category", "DelCat");
        assertThat(details).containsEntry("severity", "MEDIUM");
        assertThat(details).containsEntry("errorPattern", "del-pattern");
    }

    @Test
    void delete_notFoundDoesNotCallAudit() {
        when(errorRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.delete(999L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(auditService, never()).logDelete(any(), any(), any());
    }

    @Test
    void delete_auditExceptionDoesNotPropagateAndDeleteCompletes() {
        when(errorRepository.findById(3L)).thenReturn(Optional.of(entity(3L)));
        doThrow(new RuntimeException("audit failure")).when(auditService).logDelete(any(), any(), any());

        service.delete(3L);

        verify(errorRepository).deleteById(3L);
    }
}
