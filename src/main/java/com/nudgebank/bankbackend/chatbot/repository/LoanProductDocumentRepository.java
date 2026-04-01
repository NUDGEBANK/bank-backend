package com.nudgebank.bankbackend.chatbot.repository;

import com.nudgebank.bankbackend.chatbot.domain.LoanProductDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductDocumentRepository extends JpaRepository<LoanProductDocument, Long> {
}