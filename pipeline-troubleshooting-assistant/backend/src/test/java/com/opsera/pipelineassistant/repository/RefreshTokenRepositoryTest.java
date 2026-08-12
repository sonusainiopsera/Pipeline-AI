package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.testutil.RefreshTokenTestFactory;
import com.opsera.pipelineassistant.testutil.UserTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // Users are persisted but NOT cleared in @BeforeEach so they stay managed
    // (clearing would detach them, causing DetachedObjectException when building
    // RefreshToken instances that reference them via @ManyToOne).
    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user      = entityManager.persistAndFlush(UserTestFactory.analyst());
        otherUser = entityManager.persistAndFlush(UserTestFactory.withEmail("other@example.com"));
    }

    // ── AC3: save and findByTokenHash ─────────────────────────────────────────

    @Test
    void saveAndFindByTokenHash_returnsToken() {
        RefreshToken token = entityManager.persistAndFlush(
                RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.clear();

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(RefreshTokenTestFactory.HASH_A);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(token.getId());
        assertThat(found.get().getTokenHash()).isEqualTo(RefreshTokenTestFactory.HASH_A);
    }

    @Test
    void findByTokenHash_returnsEmptyForUnknownHash() {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("f".repeat(64));

        assertThat(found).isEmpty();
    }

    @Test
    void savePopulatesCreatedAt() {
        RefreshToken token = entityManager.persistAndFlush(
                RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.clear();

        RefreshToken retrieved = refreshTokenRepository.findById(token.getId()).orElseThrow();

        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    // ── AC4: countByUserId for concurrent session tracking ───────────────────

    @Test
    void countByUserId_returnsCorrectCountForSingleUser() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_B));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_C));
        entityManager.clear();

        assertThat(refreshTokenRepository.countByUserId(user.getId())).isEqualTo(3);
    }

    @Test
    void countByUserId_doesNotCountTokensOfOtherUsers() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user,      RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(otherUser, RefreshTokenTestFactory.HASH_B));
        entityManager.clear();

        assertThat(refreshTokenRepository.countByUserId(user.getId())).isEqualTo(1);
        assertThat(refreshTokenRepository.countByUserId(otherUser.getId())).isEqualTo(1);
    }

    @Test
    void countByUserId_returnsZeroWhenUserHasNoTokens() {
        assertThat(refreshTokenRepository.countByUserId(user.getId())).isZero();
    }

    // ── deleteByTokenHash ─────────────────────────────────────────────────────

    @Test
    void deleteByTokenHash_removesOnlyMatchingToken() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_B));
        entityManager.clear();

        refreshTokenRepository.deleteByTokenHash(RefreshTokenTestFactory.HASH_A);

        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenTestFactory.HASH_A)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenTestFactory.HASH_B)).isPresent();
    }

    @Test
    void deleteByTokenHash_isNoOpForNonExistentHash() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.clear();

        refreshTokenRepository.deleteByTokenHash(RefreshTokenTestFactory.HASH_C);

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    // ── deleteByUserId ────────────────────────────────────────────────────────

    @Test
    void deleteByUserId_removesAllTokensForThatUser() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user,      RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user,      RefreshTokenTestFactory.HASH_B));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(otherUser, RefreshTokenTestFactory.HASH_C));
        entityManager.clear();

        refreshTokenRepository.deleteByUserId(user.getId());

        assertThat(refreshTokenRepository.countByUserId(user.getId())).isZero();
        assertThat(refreshTokenRepository.countByUserId(otherUser.getId())).isEqualTo(1);
    }

    // ── AC5: deleteAllByExpiresAtBefore ───────────────────────────────────────

    @Test
    void deleteAllByExpiresAtBefore_removesOnlyExpiredTokens() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.expired(user, RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.expired(user, RefreshTokenTestFactory.HASH_B));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user,   RefreshTokenTestFactory.HASH_C));
        entityManager.clear();

        refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenTestFactory.HASH_C)).isPresent();
    }

    @Test
    void deleteAllByExpiresAtBefore_isNoOpWhenNoTokensExpired() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_B));
        entityManager.clear();

        refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now().minusDays(30));

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void deleteAllByExpiresAtBefore_isNoOpWhenTableIsEmpty() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());

        assertThat(refreshTokenRepository.count()).isZero();
    }

    // ── Unique constraint on tokenHash ────────────────────────────────────────

    @Test
    void uniqueTokenHashConstraintPreventssDuplicateStorage() {
        entityManager.persistAndFlush(RefreshTokenTestFactory.valid(user, RefreshTokenTestFactory.HASH_A));

        assertThatThrownBy(() ->
                entityManager.persistAndFlush(
                        RefreshTokenTestFactory.valid(otherUser, RefreshTokenTestFactory.HASH_A))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
