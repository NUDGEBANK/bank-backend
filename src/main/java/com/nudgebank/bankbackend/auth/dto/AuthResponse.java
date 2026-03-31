package com.nudgebank.bankbackend.auth.dto;

public record AuthResponse(
    boolean ok,
    String message
) {}
