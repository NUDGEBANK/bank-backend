package com.nudgebank.bankbackend.chatbot.controller;

import com.nudgebank.bankbackend.chatbot.service.ChatbotService;
import com.nudgebank.bankbackend.chatbot.service.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatbotService chatbotService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<String> chat(@RequestBody Map<String, String> body) {

        String userId = body.get("userId");
        String message = body.get("message");

        // 1. 유저 데이터 조회
        Map<String, Object> userInfo = userService.getUserInfo(userId);

        // 2. FastAPI 호출
        String answer = chatbotService.ask(userId, message);

        return ResponseEntity.ok(answer);
    }
}