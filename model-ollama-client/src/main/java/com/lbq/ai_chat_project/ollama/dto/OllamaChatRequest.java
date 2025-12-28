package com.lbq.ai_chat_project.ollama.dto;

import lombok.Data;

import java.util.List;

@Data
public class OllamaChatRequest {
    private String model = "qwen:latest"; // 默认模型
    private String prompt;
    private List<Message> messages;
    private boolean stream = false;

    @Data
    public static class Message {
        private String role;      // "user", "assistant"
        private String content;
    }
}