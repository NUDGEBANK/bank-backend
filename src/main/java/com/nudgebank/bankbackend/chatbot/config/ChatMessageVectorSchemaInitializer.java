package com.nudgebank.bankbackend.chatbot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageVectorSchemaInitializer {

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
    }
}
