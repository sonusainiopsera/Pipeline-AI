package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyzedLogRepository extends JpaRepository<AnalyzedLog, Long> {

    List<AnalyzedLog> findTop50ByOrderByCreatedAtDesc();

    Page<AnalyzedLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a.category, COUNT(a) FROM AnalyzedLog a GROUP BY a.category")
    List<Object[]> categoryCounts();
}
