package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
