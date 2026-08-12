package com.opsera.pipelineassistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "analyzed_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzedLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String logText;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String suggestedFix;

    @Column(columnDefinition = "TEXT")
    private String customerUpdate;

    private String severity;

    private Integer confidence;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
