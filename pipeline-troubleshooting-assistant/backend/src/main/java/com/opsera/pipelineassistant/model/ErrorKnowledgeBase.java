package com.opsera.pipelineassistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_knowledge_base")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorKnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String errorPattern;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String solution;

    private String severity;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
