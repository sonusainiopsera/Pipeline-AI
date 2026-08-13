package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.audit.AuditLogSpecification;
import com.opsera.pipelineassistant.dto.Responses.AuditLogDTO;
import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> findAll(
            Pageable pageable,
            String action,
            String resourceType,
            String actorEmail,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Specification<AuditLog> spec = Specification
                .where(AuditLogSpecification.byAction(action))
                .and(AuditLogSpecification.byResourceType(resourceType))
                .and(AuditLogSpecification.byActorEmailContaining(actorEmail))
                .and(AuditLogSpecification.byDateRange(startDate, endDate));

        log.debug("Querying audit logs with filters: action={}, resourceType={}, actorEmail={}, startDate={}, endDate={}",
                action, resourceType, actorEmail, startDate, endDate);

        return auditLogRepository.findAll(spec, pageable).map(AuditLogDTO::from);
    }
}
