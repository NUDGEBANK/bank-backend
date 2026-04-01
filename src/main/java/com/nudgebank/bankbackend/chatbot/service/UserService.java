package com.nudgebank.bankbackend.chatbot.service;

import com.nudgebank.bankbackend.auth.entity.User;
import com.nudgebank.bankbackend.auth.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Map<String, Object> getUserInfo(String userId) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> result = new HashMap<>();
        result.put("name", user.getName());

        // 현재 DB에 없는 값 → 예시로 유지
        result.put("income", 3000);
        result.put("creditScore", 720);

        return result;
    }
}