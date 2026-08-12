package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeBaseServiceCacheTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ErrorRepository errorRepository;

    private static final ErrorKnowledgeBase ENTRY = ErrorKnowledgeBase.builder()
            .id(1L)
            .errorPattern("test,pattern")
            .category("Test")
            .rootCause("Test root cause")
            .solution("Test solution")
            .severity("LOW")
            .build();

    @BeforeEach
    void evictCache() {
        cacheManager.getCache("knowledgeBase").clear();
        when(errorRepository.findAll()).thenReturn(List.of(ENTRY));
    }

    @Test
    void repeatedCallsResultInOnlyOneRepositoryInvocation() {
        List<ErrorKnowledgeBase> first = knowledgeBaseService.getAllEntries();
        List<ErrorKnowledgeBase> second = knowledgeBaseService.getAllEntries();
        List<ErrorKnowledgeBase> third = knowledgeBaseService.getAllEntries();

        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);

        // Cache hit: repository must have been called exactly once despite three invocations
        verify(errorRepository, times(1)).findAll();
    }

    @Test
    void createEvictsCache() {
        knowledgeBaseService.getAllEntries();

        ErrorKnowledgeBase newEntry = ErrorKnowledgeBase.builder()
                .errorPattern("new,pattern")
                .category("New")
                .rootCause("New cause")
                .solution("New fix")
                .severity("LOW")
                .build();
        when(errorRepository.save(newEntry)).thenReturn(newEntry);
        knowledgeBaseService.create(newEntry);

        knowledgeBaseService.getAllEntries();

        // First call before evict + one call after evict = 2 total
        verify(errorRepository, times(2)).findAll();
    }

    @Test
    void deleteEvictsCache() {
        knowledgeBaseService.getAllEntries();

        when(errorRepository.findById(1L)).thenReturn(java.util.Optional.of(ENTRY));
        knowledgeBaseService.delete(1L);

        knowledgeBaseService.getAllEntries();

        verify(errorRepository, times(2)).findAll();
    }
}
