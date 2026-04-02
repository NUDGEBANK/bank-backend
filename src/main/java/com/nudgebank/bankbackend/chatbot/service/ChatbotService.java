package com.nudgebank.bankbackend.chatbot.service;

import com.nudgebank.bankbackend.chatbot.dto.ChatRequestDto;
import com.nudgebank.bankbackend.chatbot.dto.ChatResponseDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final WebClient webClient;
    private final UserService userService;

    public String ask(String userId, String message) {

        // 🔥 DB에서 유저 정보 조회
        Map<String, Object> userInfo = userService.getUserInfo(userId);

        ChatRequestDto req = new ChatRequestDto();
        req.setUserId(userId);
        req.setMessage(message);
        req.setUserInfo(userInfo);

        ChatResponseDto res = webClient.post()
                .uri("/chat")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(ChatResponseDto.class)
                .block();

        return res.getAnswer();
    }
}