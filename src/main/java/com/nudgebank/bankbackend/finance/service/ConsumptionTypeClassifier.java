package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import org.springframework.stereotype.Component;

@Component
public class ConsumptionTypeClassifier {

    public ConsumptionType classify(CardTransaction transaction) {
        String categoryName = transaction.getCategory() != null
                ? safe(transaction.getCategory().getCategoryName())
                : "";
        return switch (categoryName) {
            case "주점", "노래방" -> ConsumptionType.RISK;
            case "택시", "대중교통", "편의점", "마트", "병원", "약국", "공과금", "통신비" -> ConsumptionType.ESSENTIAL;
            case "영화관", "문화취미(pc방, 헬스장 등)", "백화점", "의류", "서점", "미용실", "카페" ->
                    ConsumptionType.DISCRETIONARY;
            case "음식점", "넛지뱅크" -> ConsumptionType.NORMAL;
            default -> ConsumptionType.NORMAL;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
