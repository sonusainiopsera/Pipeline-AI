package com.opsera.pipelineassistant.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void cacheManagerIsCaffeineType() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    void knowledgeBaseCacheIsRegistered() {
        assertThat(cacheManager.getCacheNames()).contains("knowledgeBase");
    }

    @Test
    void knowledgeBaseCacheHasStatsRecordingEnabled() {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache("knowledgeBase");
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        nativeCache.getIfPresent("probe-key");
        assertThat(nativeCache.stats().missCount()).isGreaterThanOrEqualTo(1L);
    }
}
