package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ErrorRepository extends JpaRepository<ErrorKnowledgeBase, Long> {
}
