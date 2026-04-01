package com.nudgebank.bankbackend.chatbot.repository;

import com.nudgebank.bankbackend.chatbot.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionSessionIdOrderByCreatedAt(UUID sessionId);
}