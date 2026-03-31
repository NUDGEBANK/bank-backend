package com.nudgebank.bankbackend.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequestDto {
    @JsonProperty("user_id")
    private String userId;
    private String message;
    @JsonProperty("user_info")
    private Map<String, Object> userInfo;
}