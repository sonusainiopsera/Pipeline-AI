package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Daily cleanup scheduler that purges expired refresh tokens and clears
 * expired email verification tokens from unverified user accounts.
 *
 * Runs at 02:00 UTC. All exceptions are caught so the scheduler thread
 * is never killed by a transient database failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Token cleanup started");
        try {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            long expiredRefreshCount = refreshTokenRepository.countByExpiresAtBefore(now);
            refreshTokenRepository.deleteAllByExpiresAtBefore(now);

            int verificationTokensCleared = userRepository.clearExpiredVerificationTokens(now);

            log.info("Token cleanup completed: refreshTokensDeleted={}, verificationTokensCleared={}",
                    expiredRefreshCount, verificationTokensCleared);
        } catch (Exception e) {
            log.error("Token cleanup failed: {}", e.getMessage(), e);
        }
    }
}
