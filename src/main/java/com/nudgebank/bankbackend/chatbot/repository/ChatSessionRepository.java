package com.nudgebank.bankbackend.chatbot.repository;

import com.nudgebank.bankbackend.chatbot.domain.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByMemberId(Long memberId);
}