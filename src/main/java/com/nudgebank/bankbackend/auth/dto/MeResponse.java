package com.nudgebank.bankbackend.auth.dto;

public record MeResponse(
    Long memberId,
    String loginId,
    String name
) {}
