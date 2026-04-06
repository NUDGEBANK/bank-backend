package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.finance.dto.AgeGroupBaselineResponse;
import com.nudgebank.bankbackend.finance.service.AgeGroupBaselineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/baselines")
public class AgeGroupBaselineController {

    private final AgeGroupBaselineService ageGroupBaselineService;

    @GetMapping("/me")
    public AgeGroupBaselineResponse getBaseline(Authentication authentication) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ageGroupBaselineService.getBaseline(memberId);
    }
}