package com.nudgebank.bankbackend.chatbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan_product_documents")
@Getter
@Setter
@NoArgsConstructor
public class LoanProductDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "loan_product_id", nullable = false)
    private Long loanProductId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String content;

    // pgvector
    @Column(name = "embedding", columnDefinition = "vector")
    private float[] embedding;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
