package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final ErrorRepository errorRepository;

    public List<ErrorKnowledgeBase> findAll() {
        return errorRepository.findAll();
    }

    public ErrorKnowledgeBase findById(Long id) {
        return errorRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Known error not found"));
    }

    public ErrorKnowledgeBase create(ErrorKnowledgeBase entry) {
        return errorRepository.save(entry);
    }

    public ErrorKnowledgeBase update(Long id, ErrorKnowledgeBase entry) {
        ErrorKnowledgeBase existing = findById(id);
        existing.setErrorPattern(entry.getErrorPattern());
        existing.setCategory(entry.getCategory());
        existing.setRootCause(entry.getRootCause());
        existing.setSolution(entry.getSolution());
        existing.setSeverity(entry.getSeverity());
        return errorRepository.save(existing);
    }

    public void delete(Long id) {
        findById(id);
        errorRepository.deleteById(id);
    }
}
