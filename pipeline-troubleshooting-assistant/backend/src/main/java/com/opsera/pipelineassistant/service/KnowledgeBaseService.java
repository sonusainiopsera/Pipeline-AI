package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final ErrorRepository errorRepository;

    public List<ErrorKnowledgeBase> findAll() {
        List<ErrorKnowledgeBase> entries = errorRepository.findAll();
        log.info("Knowledge base listed, count={}", entries.size());
        return entries;
    }

    public ErrorKnowledgeBase findById(Long id) {
        return errorRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Known error not found"));
    }

    public ErrorKnowledgeBase create(ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase saved = errorRepository.save(entry);
        log.info("Knowledge base entry created, id={}, category={}", saved.getId(), saved.getCategory());
        return saved;
    }

    public ErrorKnowledgeBase update(Long id, ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase existing = findById(id);
        existing.setErrorPattern(entry.getErrorPattern());
        existing.setCategory(entry.getCategory());
        existing.setRootCause(entry.getRootCause());
        existing.setSolution(entry.getSolution());
        existing.setSeverity(entry.getSeverity());
        ErrorKnowledgeBase saved = errorRepository.save(existing);
        log.info("Knowledge base entry updated, id={}, category={}", saved.getId(), saved.getCategory());
        return saved;
    }

    public void delete(Long id) {
        findById(id);
        errorRepository.deleteById(id);
        log.info("Knowledge base entry deleted, id={}", id);
    }
}
