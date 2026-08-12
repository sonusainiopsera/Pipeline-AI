package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final ErrorRepository errorRepository;

    public List<ErrorKnowledgeBase> findAll() {
        return errorRepository.findAll();
    }

    public ErrorKnowledgeBase create(ErrorKnowledgeBase entry) {
        return errorRepository.save(entry);
    }

    public ErrorKnowledgeBase update(Long id, ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase existing = errorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Known error not found"));
        existing.setErrorPattern(entry.getErrorPattern());
        existing.setCategory(entry.getCategory());
        existing.setRootCause(entry.getRootCause());
        existing.setSolution(entry.getSolution());
        existing.setSeverity(entry.getSeverity());
        return errorRepository.save(existing);
    }

    public void delete(Long id) {
        errorRepository.deleteById(id);
    }
}
