package com.lbq.ai_chat_project.ollama.dto;

import lombok.Data;

@Data
public class OllamaChatResponse {
    private String model;
    private String response;
    private boolean done;
    private Long totalDuration;
    private Long loadDuration;
}