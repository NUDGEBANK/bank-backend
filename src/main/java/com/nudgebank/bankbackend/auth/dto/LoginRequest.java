package com.nudgebank.bankbackend.auth.dto;

public record LoginRequest(
    String userId,
    String password
) {}
