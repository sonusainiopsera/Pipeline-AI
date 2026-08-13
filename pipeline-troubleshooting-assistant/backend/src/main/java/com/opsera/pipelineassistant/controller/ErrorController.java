package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.ErrorRequest;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/errors")
@RequiredArgsConstructor
@Slf4j
public class ErrorController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PreAuthorize("hasAnyRole('ANALYST', 'KB_ADMIN', 'MANAGER')")
    @GetMapping
    public List<ErrorKnowledgeBase> findAll() {
        log.info("GET /api/errors received");
        return knowledgeBaseService.findAll();
    }

    @PreAuthorize("hasAnyRole('KB_ADMIN', 'MANAGER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ErrorKnowledgeBase create(@Valid @RequestBody ErrorRequest request) {
        log.info("POST /api/errors received");
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
            .errorPattern(request.getErrorPattern())
            .category(request.getCategory())
            .rootCause(request.getRootCause())
            .solution(request.getSolution())
            .severity(request.getSeverity())
            .build();
        return knowledgeBaseService.create(entry);
    }

    @PreAuthorize("hasAnyRole('KB_ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ErrorKnowledgeBase update(@PathVariable Long id, @Valid @RequestBody ErrorRequest request) {
        log.info("PUT /api/errors/{} received", id);
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
            .errorPattern(request.getErrorPattern())
            .category(request.getCategory())
            .rootCause(request.getRootCause())
            .solution(request.getSolution())
            .severity(request.getSeverity())
            .build();
        return knowledgeBaseService.update(id, entry);
    }

    @PreAuthorize("hasAnyRole('KB_ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        log.info("DELETE /api/errors/{} received", id);
        knowledgeBaseService.delete(id);
    }
}
