package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyzedLogRepository extends JpaRepository<AnalyzedLog, Long> {

    List<AnalyzedLog> findTop50ByOrderByCreatedAtDesc();

    List<AnalyzedLog> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Page<AnalyzedLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a.category, COUNT(a) FROM AnalyzedLog a GROUP BY a.category")
    List<Object[]> categoryCounts();

    @Modifying
    @Transactional
    @Query("DELETE FROM AnalyzedLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
