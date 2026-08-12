package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.ErrorRequest;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/errors")
@RequiredArgsConstructor
public class ErrorController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public List<ErrorKnowledgeBase> findAll() {
        return knowledgeBaseService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ErrorKnowledgeBase create(@Valid @RequestBody ErrorRequest request) {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
            .errorPattern(request.getErrorPattern())
            .category(request.getCategory())
            .rootCause(request.getRootCause())
            .solution(request.getSolution())
            .severity(request.getSeverity())
            .build();
        return knowledgeBaseService.create(entry);
    }

    @PutMapping("/{id}")
    public ErrorKnowledgeBase update(@PathVariable Long id, @Valid @RequestBody ErrorRequest request) {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
            .errorPattern(request.getErrorPattern())
            .category(request.getCategory())
            .rootCause(request.getRootCause())
            .solution(request.getSolution())
            .severity(request.getSeverity())
            .build();
        return knowledgeBaseService.update(id, entry);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
    }
}
