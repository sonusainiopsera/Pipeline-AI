package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private ErrorKnowledgeBase buildKnowledgeBaseEntity(Long id,
                                                         String errorPattern,
                                                         String category,
                                                         String rootCause,
                                                         String solution,
                                                         String severity) {
        return ErrorKnowledgeBase.builder()
                .id(id)
                .errorPattern(errorPattern)
                .category(category)
                .rootCause(rootCause)
                .solution(solution)
                .severity(severity)
                .build();
    }

    // ── 1. findAll returns all entries ────────────────────────────────────────
    @Test
    void shouldReturnAllEntries() {
        List<ErrorKnowledgeBase> entries = List.of(
                buildKnowledgeBaseEntity(1L, "pattern1", "Memory", "OOM", "Fix heap", "HIGH"),
                buildKnowledgeBaseEntity(2L, "pattern2", "Network", "Timeout", "Check net", "MEDIUM"),
                buildKnowledgeBaseEntity(3L, "pattern3", "Docker", "Build fail", "Fix image", "HIGH")
        );
        when(errorRepository.findAll()).thenReturn(entries);

        List<ErrorKnowledgeBase> result = knowledgeBaseService.findAll();

        assertThat(result).hasSize(3);
        assertThat(result).isEqualTo(entries);
    }

    // ── 2. findAll returns empty list when no entries ─────────────────────────
    @Test
    void shouldReturnEmptyListWhenNoEntriesExist() {
        when(errorRepository.findAll()).thenReturn(Collections.emptyList());

        List<ErrorKnowledgeBase> result = knowledgeBaseService.findAll();

        assertThat(result).isEmpty();
    }

    // ── 3. findById returns entity when found ─────────────────────────────────
    @Test
    void shouldReturnEntityWhenFoundById() {
        ErrorKnowledgeBase entity = buildKnowledgeBaseEntity(
                42L, "oom,heap", "Memory", "Heap exhausted", "Increase Xmx", "HIGH");
        when(errorRepository.findById(42L)).thenReturn(Optional.of(entity));

        ErrorKnowledgeBase result = knowledgeBaseService.findById(42L);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getCategory()).isEqualTo("Memory");
        assertThat(result.getErrorPattern()).isEqualTo("oom,heap");
    }

    // ── 4. findById throws NOT_FOUND when entity is missing ───────────────────
    @Test
    void shouldThrowNotFoundWhenEntityDoesNotExist() {
        when(errorRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> knowledgeBaseService.findById(999L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getReason()).isEqualTo("Known error not found");
    }

    // ── 5. create saves entity with correct fields ────────────────────────────
    @Test
    void shouldSaveNewEntityWithCorrectFields() {
        ErrorKnowledgeBase input = buildKnowledgeBaseEntity(
                null, "docker,build", "Docker", "Build failed", "Fix Dockerfile", "HIGH");

        ArgumentCaptor<ErrorKnowledgeBase> captor = ArgumentCaptor.forClass(ErrorKnowledgeBase.class);
        when(errorRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ErrorKnowledgeBase result = knowledgeBaseService.create(input);

        verify(errorRepository, times(1)).save(any(ErrorKnowledgeBase.class));
        ErrorKnowledgeBase saved = captor.getValue();
        assertThat(saved.getErrorPattern()).isEqualTo("docker,build");
        assertThat(saved.getCategory()).isEqualTo("Docker");
        assertThat(saved.getRootCause()).isEqualTo("Build failed");
        assertThat(saved.getSolution()).isEqualTo("Fix Dockerfile");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(result.getCategory()).isEqualTo("Docker");
    }

    // ── 6. update overwrites all fields on existing entity ───────────────────
    @Test
    void shouldUpdateExistingEntityFields() {
        ErrorKnowledgeBase existing = buildKnowledgeBaseEntity(
                10L, "old-pattern", "OldCategory", "Old cause", "Old fix", "LOW");
        when(errorRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ErrorKnowledgeBase updatePayload = buildKnowledgeBaseEntity(
                null, "new-pattern", "NewCategory", "New cause", "New fix", "HIGH");

        ErrorKnowledgeBase result = knowledgeBaseService.update(10L, updatePayload);

        ArgumentCaptor<ErrorKnowledgeBase> captor = ArgumentCaptor.forClass(ErrorKnowledgeBase.class);
        verify(errorRepository).save(captor.capture());
        ErrorKnowledgeBase saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getErrorPattern()).isEqualTo("new-pattern");
        assertThat(saved.getCategory()).isEqualTo("NewCategory");
        assertThat(saved.getRootCause()).isEqualTo("New cause");
        assertThat(saved.getSolution()).isEqualTo("New fix");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(result.getCategory()).isEqualTo("NewCategory");
    }

    // ── 7. update throws NOT_FOUND when target entity is missing ─────────────
    @Test
    void shouldThrowNotFoundOnUpdateWhenEntityMissing() {
        when(errorRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> knowledgeBaseService.update(404L, buildKnowledgeBaseEntity(
                        null, "p", "C", "R", "S", "LOW")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(errorRepository, never()).save(any());
    }

    // ── 8. delete calls deleteById with correct ID ────────────────────────────
    @Test
    void shouldDeleteExistingEntity() {
        ErrorKnowledgeBase entity = buildKnowledgeBaseEntity(
                7L, "pattern", "Category", "Cause", "Fix", "MEDIUM");
        when(errorRepository.findById(7L)).thenReturn(Optional.of(entity));

        knowledgeBaseService.delete(7L);

        verify(errorRepository, times(1)).deleteById(7L);
    }

    // ── 9. delete throws NOT_FOUND and never calls deleteById when missing ────
    @Test
    void shouldThrowNotFoundOnDeleteWhenEntityMissing() {
        when(errorRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> knowledgeBaseService.delete(404L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(errorRepository, never()).deleteById(any());
    }

    // ── 10. create increments kb.operations counter with tag operation=create ─
    @Test
    void shouldIncrementKbOperationsCounterOnCreate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KnowledgeBaseService svc = new KnowledgeBaseService(errorRepository, registry);

        ErrorKnowledgeBase input = buildKnowledgeBaseEntity(null, "p", "C", "R", "S", "LOW");
        when(errorRepository.save(input)).thenReturn(input);

        svc.create(input);

        assertThat(registry.counter("kb.operations", "operation", "create").count()).isEqualTo(1.0);
    }

    // ── 11. update increments kb.operations counter with tag operation=update ─
    @Test
    void shouldIncrementKbOperationsCounterOnUpdate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KnowledgeBaseService svc = new KnowledgeBaseService(errorRepository, registry);

        ErrorKnowledgeBase existing = buildKnowledgeBaseEntity(5L, "old", "OldCat", "OldR", "OldS", "LOW");
        when(errorRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(errorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.update(5L, buildKnowledgeBaseEntity(null, "new", "NewCat", "NewR", "NewS", "HIGH"));

        assertThat(registry.counter("kb.operations", "operation", "update").count()).isEqualTo(1.0);
    }

    // ── 12. delete increments kb.operations counter with tag operation=delete ─
    @Test
    void shouldIncrementKbOperationsCounterOnDelete() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KnowledgeBaseService svc = new KnowledgeBaseService(errorRepository, registry);

        ErrorKnowledgeBase existing = buildKnowledgeBaseEntity(3L, "p", "C", "R", "S", "LOW");
        when(errorRepository.findById(3L)).thenReturn(Optional.of(existing));

        svc.delete(3L);

        assertThat(registry.counter("kb.operations", "operation", "delete").count()).isEqualTo(1.0);
    }
}
