package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ErrorKnowledgeBaseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ErrorRepository repository;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ErrorKnowledgeBase buildEntry(String category) {
        return ErrorKnowledgeBase.builder()
                .errorPattern("java.lang.Exception, error, failure")
                .category(category)
                .rootCause("Root cause for " + category)
                .solution("Apply fix for " + category)
                .severity("HIGH")
                .build();
    }

    // ── CRUD operations ────────────────────────────────────────────────────────

    @Test
    void shouldSaveAndFindById() {
        ErrorKnowledgeBase saved = repository.save(buildEntry("BUILD_FAILURE"));
        entityManager.flush();
        entityManager.clear();

        Optional<ErrorKnowledgeBase> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCategory()).isEqualTo("BUILD_FAILURE");
        assertThat(found.get().getErrorPattern()).isEqualTo("java.lang.Exception, error, failure");
    }

    @Test
    void shouldFindAllPersistedEntries() {
        repository.save(buildEntry("BUILD_FAILURE"));
        repository.save(buildEntry("AUTHENTICATION"));
        repository.save(buildEntry("DOCKER"));
        entityManager.flush();
        entityManager.clear();

        List<ErrorKnowledgeBase> all = repository.findAll();

        assertThat(all).hasSize(3);
        assertThat(all).extracting(ErrorKnowledgeBase::getCategory)
                .containsExactlyInAnyOrder("BUILD_FAILURE", "AUTHENTICATION", "DOCKER");
    }

    @Test
    void shouldDeleteById() {
        ErrorKnowledgeBase saved = repository.save(buildEntry("NETWORK"));
        entityManager.flush();
        Long id = saved.getId();

        repository.deleteById(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentId() {
        Optional<ErrorKnowledgeBase> found = repository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldPersistAllEntityFields() {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
                .errorPattern("OutOfMemoryError, heap space")
                .category("MEMORY")
                .rootCause("JVM heap space exhausted during build")
                .solution("Increase -Xmx JVM argument")
                .severity("CRITICAL")
                .build();

        ErrorKnowledgeBase saved = repository.saveAndFlush(entry);
        entityManager.clear();

        ErrorKnowledgeBase found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getErrorPattern()).isEqualTo("OutOfMemoryError, heap space");
        assertThat(found.getCategory()).isEqualTo("MEMORY");
        assertThat(found.getRootCause()).isEqualTo("JVM heap space exhausted during build");
        assertThat(found.getSolution()).isEqualTo("Increase -Xmx JVM argument");
        assertThat(found.getSeverity()).isEqualTo("CRITICAL");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReturnCorrectCountAfterSaves() {
        assertThat(repository.count()).isZero();

        repository.save(buildEntry("TYPE_A"));
        repository.save(buildEntry("TYPE_B"));
        entityManager.flush();

        assertThat(repository.count()).isEqualTo(2);
    }
}
