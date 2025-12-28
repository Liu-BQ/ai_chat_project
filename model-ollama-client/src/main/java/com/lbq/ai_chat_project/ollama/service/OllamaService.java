package com.lbq.ai_chat_project.ollama.service;

import reactor.core.publisher.Mono;

public interface OllamaService {
    Mono<String> generateResponse(String userMessage);
}
