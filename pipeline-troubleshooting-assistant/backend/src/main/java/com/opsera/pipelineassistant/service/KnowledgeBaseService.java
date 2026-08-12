package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final ErrorRepository errorRepository;
    private final MeterRegistry meterRegistry;
    private final AuditService auditService;

    /**
     * Returns all knowledge base entries, serving from the in-process Caffeine cache
     * on subsequent calls within the TTL window. Used by AnalysisService so pattern
     * matching does not trigger a full table scan on every request.
     */
    @Cacheable("knowledgeBase")
    public List<ErrorKnowledgeBase> getAllEntries() {
        List<ErrorKnowledgeBase> entries = errorRepository.findAll();
        log.debug("Knowledge base loaded from database, count={}", entries.size());
        return entries;
    }

    public List<ErrorKnowledgeBase> findAll() {
        List<ErrorKnowledgeBase> entries = errorRepository.findAll();
        log.info("Knowledge base listed, count={}", entries.size());
        return entries;
    }

    public ErrorKnowledgeBase findById(Long id) {
        return errorRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Known error not found"));
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public ErrorKnowledgeBase create(ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase saved = errorRepository.save(entry);
        log.info("Knowledge base entry created, id={}, category={}", saved.getId(), saved.getCategory());
        try {
            meterRegistry.counter("kb.operations", "operation", "create").increment();
        } catch (Exception metricEx) {
            log.warn("Failed to record kb.operations metric: {}", metricEx.getMessage());
        }
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("category", saved.getCategory());
            details.put("severity", saved.getSeverity());
            details.put("errorPattern", saved.getErrorPattern());
            auditService.logCreate("KNOWLEDGE_BASE", String.valueOf(saved.getId()), details);
        } catch (Exception auditEx) {
            log.error("Failed to record audit event for KB create id={}: {}", saved.getId(), auditEx.getMessage());
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public ErrorKnowledgeBase update(Long id, ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase existing = findById(id);

        Map<String, Object> beforeState = new HashMap<>();
        beforeState.put("category", existing.getCategory());
        beforeState.put("severity", existing.getSeverity());
        beforeState.put("errorPattern", existing.getErrorPattern());

        existing.setErrorPattern(entry.getErrorPattern());
        existing.setCategory(entry.getCategory());
        existing.setRootCause(entry.getRootCause());
        existing.setSolution(entry.getSolution());
        existing.setSeverity(entry.getSeverity());
        ErrorKnowledgeBase saved = errorRepository.save(existing);
        log.info("Knowledge base entry updated, id={}, category={}", saved.getId(), saved.getCategory());
        try {
            meterRegistry.counter("kb.operations", "operation", "update").increment();
        } catch (Exception metricEx) {
            log.warn("Failed to record kb.operations metric: {}", metricEx.getMessage());
        }
        try {
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("category", saved.getCategory());
            afterState.put("severity", saved.getSeverity());
            afterState.put("errorPattern", saved.getErrorPattern());
            Map<String, Object> details = new HashMap<>();
            details.put("before", beforeState);
            details.put("after", afterState);
            auditService.logUpdate("KNOWLEDGE_BASE", String.valueOf(saved.getId()), details);
        } catch (Exception auditEx) {
            log.error("Failed to record audit event for KB update id={}: {}", saved.getId(), auditEx.getMessage());
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "knowledgeBase", allEntries = true)
    public void delete(Long id) {
        ErrorKnowledgeBase entity = findById(id);
        errorRepository.deleteById(id);
        log.info("Knowledge base entry deleted, id={}", id);
        try {
            meterRegistry.counter("kb.operations", "operation", "delete").increment();
        } catch (Exception metricEx) {
            log.warn("Failed to record kb.operations metric: {}", metricEx.getMessage());
        }
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("category", entity.getCategory());
            details.put("severity", entity.getSeverity());
            details.put("errorPattern", entity.getErrorPattern());
            auditService.logDelete("KNOWLEDGE_BASE", String.valueOf(id), details);
        } catch (Exception auditEx) {
            log.error("Failed to record audit event for KB delete id={}: {}", id, auditEx.getMessage());
        }
    }
}
