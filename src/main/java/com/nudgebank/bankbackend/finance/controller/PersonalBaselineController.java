package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.finance.dto.ConsumerBaselineResponse;
import com.nudgebank.bankbackend.finance.dto.FinalBaselineResponse;
import com.nudgebank.bankbackend.finance.service.PersonalBaselineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/baselines")
public class PersonalBaselineController {

    private final PersonalBaselineService personalBaselineService;

    @GetMapping("/final/me/{transactionId}")
    public FinalBaselineResponse getFinalBaseline(Authentication authentication, @PathVariable Long transactionId) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return personalBaselineService.calculateAndGetFinalBaseline(memberId, transactionId);
    }

    @GetMapping("/consumer/me")
    public ConsumerBaselineResponse getLatestConsumerBaseline(Authentication authentication) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return personalBaselineService.getLatestConsumerBaseline(memberId);
    }
}
