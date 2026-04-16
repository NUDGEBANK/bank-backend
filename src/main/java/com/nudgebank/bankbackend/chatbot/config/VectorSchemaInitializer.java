package com.nudgebank.bankbackend.chatbot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VectorSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        jdbcTemplate.execute("""
            ALTER TABLE chat_messages
            ADD COLUMN IF NOT EXISTS embedding vector(768)
            """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS chat_messages_embedding_hnsw_idx
            ON chat_messages USING hnsw (embedding vector_cosine_ops)
            """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS loan_product_documents_embedding_hnsw_idx
            ON loan_product_documents USING hnsw (embedding vector_cosine_ops)
            """);
    }
}
