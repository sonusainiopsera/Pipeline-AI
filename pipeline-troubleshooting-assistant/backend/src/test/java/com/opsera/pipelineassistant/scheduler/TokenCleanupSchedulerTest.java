package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TokenCleanupScheduler scheduler;

    @Test
    void cleanup_callsDeleteAllByExpiresAtBeforeWithCurrentTimestamp() {
        when(refreshTokenRepository.countByExpiresAtBefore(any())).thenReturn(3L);
        when(userRepository.clearExpiredVerificationTokens(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        scheduler.cleanupExpiredTokens();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteAllByExpiresAtBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void cleanup_callsClearExpiredVerificationTokensWithCurrentTimestamp() {
        when(refreshTokenRepository.countByExpiresAtBefore(any())).thenReturn(0L);
        when(userRepository.clearExpiredVerificationTokens(any())).thenReturn(2);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        scheduler.cleanupExpiredTokens();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).clearExpiredVerificationTokens(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void cleanup_logsCountsAndCompletesSuccessfully() {
        when(refreshTokenRepository.countByExpiresAtBefore(any())).thenReturn(5L);
        when(userRepository.clearExpiredVerificationTokens(any())).thenReturn(3);

        assertThatCode(() -> scheduler.cleanupExpiredTokens()).doesNotThrowAnyException();
        verify(refreshTokenRepository).deleteAllByExpiresAtBefore(any());
        verify(userRepository).clearExpiredVerificationTokens(any());
    }

    @Test
    void cleanup_zeroExpiredTokens_completesWithoutError() {
        when(refreshTokenRepository.countByExpiresAtBefore(any())).thenReturn(0L);
        when(userRepository.clearExpiredVerificationTokens(any())).thenReturn(0);

        assertThatCode(() -> scheduler.cleanupExpiredTokens()).doesNotThrowAnyException();
    }

    @Test
    void cleanup_doesNotPropagateExceptions() {
        when(refreshTokenRepository.countByExpiresAtBefore(any()))
                .thenThrow(new RuntimeException("simulated DB failure"));

        assertThatCode(() -> scheduler.cleanupExpiredTokens()).doesNotThrowAnyException();
    }

    @Test
    void cleanup_refreshTokenException_doesNotCallUserRepository() {
        when(refreshTokenRepository.countByExpiresAtBefore(any()))
                .thenThrow(new RuntimeException("simulated DB failure"));

        scheduler.cleanupExpiredTokens();

        verify(userRepository, never()).clearExpiredVerificationTokens(any());
    }
}
