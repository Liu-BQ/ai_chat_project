package com.lbq.ai_chat_project.ollama.service.impl;

import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import com.lbq.ai_chat_project.ollama.dto.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.service.OllamaService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class OllamaServiceImpl implements OllamaService {

    private final OllamaClient ollamaClient;

    public OllamaServiceImpl(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public Mono<String> generateResponse(String userMessage) {
        OllamaChatRequest request = new OllamaChatRequest();
        request.setStream(false);
        request.setModel("qwen:latest");

        OllamaChatRequest.Message message = new OllamaChatRequest.Message();
        message.setRole("user");
        message.setContent(userMessage);
        request.setMessages(List.of(message));

        return ollamaClient.chat(request)
                .map(response -> response.getResponse());
    }
}