package com.nudgebank.bankbackend.deposit.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositAutoTransferScheduler {

    private final DepositAutoTransferService depositAutoTransferService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void executeDueDepositAutoTransfers() {
        LocalDate today = LocalDate.now();
        List<Long> scheduleIds = depositAutoTransferService.findDueScheduleIds(today);
        for (Long scheduleId : scheduleIds) {
            try {
                depositAutoTransferService.executeAutoTransfer(scheduleId, today);
            } catch (Exception exception) {
                log.warn("Deposit auto transfer failed. scheduleId={}", scheduleId, exception);
            }
        }
    }
}
