package com.nudgebank.bankbackend.card.dto;

public record CardIssueRequest(
    String cardHolderName,
    String phoneNumber,
    String cardPassword
) {
}
