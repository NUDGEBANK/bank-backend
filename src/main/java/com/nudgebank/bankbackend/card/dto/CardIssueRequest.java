package com.nudgebank.bankbackend.card.dto;

public record CardIssueRequest(
    String accountName,
    String cardPassword
) {
}
