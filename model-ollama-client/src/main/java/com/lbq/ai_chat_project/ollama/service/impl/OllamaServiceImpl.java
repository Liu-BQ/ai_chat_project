package com.lbq.ai_chat_project.ollama.service.impl;

import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatResponse;
import com.lbq.ai_chat_project.ollama.service.OllamaService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * Ollama 服务实现（无容错机制）。
 * <p>
 * 负责将用户输入转换为 Ollama 请求，并处理响应。
 * </p>
 * <p>
 * ✅ 简化逻辑：移除了容错相关代码
 * ✅ 保留核心功能：消息转换、响应处理
 * </p>
 */
@Service
public class OllamaServiceImpl implements OllamaService {

    private final OllamaClient ollamaClient;

    /**
     * 构造函数：注入 Ollama 客户端
     *
     * @param ollamaClient Ollama 客户端实例
     */
    public OllamaServiceImpl(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public Mono<String> generateResponse(String userMessage) {
        // 构建聊天请求
        OllamaChatRequest request = new OllamaChatRequest();
        request.setModel("qwen:latest"); // 默认模型
        request.setStream(false); // 非流式响应

        // 创建用户消息
        OllamaChatRequest.Message message = new OllamaChatRequest.Message();
        message.setRole("user");
        message.setContent(userMessage);

        // 设置消息历史
        request.setMessages(List.of(message));

        // 调用 Ollama 客户端
        return ollamaClient.chat(request)
                .map(OllamaChatResponse::getResponse);
    }
}