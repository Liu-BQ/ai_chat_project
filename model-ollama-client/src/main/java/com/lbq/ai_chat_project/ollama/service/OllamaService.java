package com.lbq.ai_chat_project.ollama.service;

import reactor.core.publisher.Mono;

/**
 * Ollama 服务接口。
 * <p>
 * 定义生成聊天响应的契约。
 * </p>
 */
public interface OllamaService {
    /**
     * 生成基于用户输入的聊天响应。
     *
     * @param userMessage 用户输入的文本
     * @return 包含模型响应的 Mono
     */
    Mono<String> generateResponse(String userMessage);
}