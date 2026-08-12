package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.testutil.UserTestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    // ── Persistence and retrieval ─────────────────────────────────────────────

    @Test
    void saveAndFindByIdReturnsUser() {
        User saved = entityManager.persistAndFlush(UserTestFactory.analyst());
        entityManager.clear();

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("analyst@example.com");
        assertThat(found.get().getRole()).isEqualTo(Role.ANALYST);
        assertThat(found.get().getDisplayName()).isEqualTo("Test Analyst");
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmailReturnsUserWhenEmailExists() {
        entityManager.persistAndFlush(UserTestFactory.withEmail("alice@example.com"));
        entityManager.clear();

        Optional<User> result = userRepository.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByEmailReturnsEmptyOptionalForNonExistentEmail() {
        Optional<User> result = userRepository.findByEmail("nonexistent@example.com");

        assertThat(result).isEmpty();
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    void existsByEmailReturnsTrueWhenEmailRegistered() {
        entityManager.persistAndFlush(UserTestFactory.withEmail("bob@example.com"));
        entityManager.clear();

        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void existsByEmailReturnsFalseWhenEmailNotRegistered() {
        assertThat(userRepository.existsByEmail("ghost@example.com")).isFalse();
    }

    // ── Unique email constraint ───────────────────────────────────────────────

    @Test
    void uniqueEmailConstraintPreventssDuplicateRegistration() {
        entityManager.persistAndFlush(UserTestFactory.withEmail("duplicate@example.com"));

        assertThatThrownBy(() ->
                entityManager.persistAndFlush(UserTestFactory.withEmail("duplicate@example.com"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Default field values ──────────────────────────────────────────────────

    @Test
    void defaultFieldValuesAreCorrectAfterPersist() {
        User saved = entityManager.persistAndFlush(UserTestFactory.analyst());
        entityManager.clear();

        User retrieved = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getMfaEnabled()).isFalse();
        assertThat(retrieved.getEmailVerified()).isFalse();
        assertThat(retrieved.getFailedLoginAttempts()).isZero();
        assertThat(retrieved.getMfaSecret()).isNull();
        assertThat(retrieved.getLockedUntil()).isNull();
        assertThat(retrieved.getCreatedAt()).isNotNull();
        assertThat(retrieved.getUpdatedAt()).isNotNull();
    }

    // ── Role enum values ──────────────────────────────────────────────────────

    @Test
    void allThreeRoleValuesCanBePersistedAndRetrieved() {
        entityManager.persistAndFlush(UserTestFactory.analyst());
        entityManager.persistAndFlush(UserTestFactory.kbAdmin());
        entityManager.persistAndFlush(UserTestFactory.manager());
        entityManager.clear();

        assertThat(userRepository.findByEmail("analyst@example.com")
                .map(User::getRole)).contains(Role.ANALYST);
        assertThat(userRepository.findByEmail("kbadmin@example.com")
                .map(User::getRole)).contains(Role.KB_ADMIN);
        assertThat(userRepository.findByEmail("manager@example.com")
                .map(User::getRole)).contains(Role.MANAGER);
    }
}
