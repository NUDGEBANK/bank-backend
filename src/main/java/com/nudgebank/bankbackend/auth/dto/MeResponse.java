package com.nudgebank.bankbackend.auth.dto;

public record MeResponse(
    Long memberId,
    String loginId,
    String name,
    String birth,
    String gender,
    String phoneNumber
) {}
