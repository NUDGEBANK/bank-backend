package com.nudgebank.bankbackend.chatbot.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public Map<String, Object> getUserInfo(String userId) {

        Map<String, Object> user = new HashMap<>();
        user.put("name", "홍길동");
        user.put("income", 3000);
        user.put("creditScore", 720);

        return user;
    }
}