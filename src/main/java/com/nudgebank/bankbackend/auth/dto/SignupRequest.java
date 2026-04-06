package com.nudgebank.bankbackend.auth.dto;

import java.time.LocalDate;

public record SignupRequest(
    String name,
    String userId,
    String password,
    LocalDate birth,
    String gender,
    String phoneNumber
) {}
