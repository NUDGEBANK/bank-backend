package com.nudgebank.bankbackend.credit.repository;

import com.nudgebank.bankbackend.credit.domain.CreditHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {
  Optional<CreditHistory> findTopByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(Long memberId);

  List<CreditHistory> findTop2ByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(Long memberId);
}
