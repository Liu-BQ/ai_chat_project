package com.lbq.ai_chat_project.ollama.service;

import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * Ollama 客户端管理器，用于协调本地与远程 Ollama 服务实例。
 * <p>
 * 提供以下能力：
 * <ul>
 *   <li>动态切换当前使用的 Ollama endpoint（local / remote）</li>
 *   <li>查询当前 endpoint 下所有可用模型</li>
 *   <li>指定模型发起聊天请求</li>
 * </ul>
 * <p>
 * 注意：本类不是 {@link OllamaService} 接口的实现，
 * 而是作为其底层支撑组件，也可被管理 API 直接调用。
 */
@Slf4j
@Service
public class OllamaServiceManager {

    private final OllamaClient localClient;
    private final OllamaClient remoteClient;

    /**
     * 当前激活的 endpoint 名称，支持 "local" 或 "remote"。
     * 使用 volatile 保证多线程下可见性。
     * -- GETTER --
     *  获取当前激活的 endpoint 名称。
     *
     * @return "local" 或 "remote"

     */
    @Getter
    private volatile String activeEndpoint = "local";

    /**
     * 构造函数，注入两个命名的 OllamaClient 实例。
     *
     * @param localClient  本地 Ollama 客户端（通常指向 http://localhost:11434）
     * @param remoteClient 远程 Ollama 客户端（如生产环境或高性能节点）
     */
    public OllamaServiceManager(
            @Qualifier("localOllamaClient") OllamaClient localClient,
            @Qualifier("remoteOllamaClient") OllamaClient remoteClient) {
        this.localClient = localClient;
        this.remoteClient = remoteClient;
    }

    /**
     * 获取当前激活的 Ollama 客户端。
     *
     * @return 激活的客户端实例
     */
    public OllamaClient getActiveClient() {
        return "remote".equals(activeEndpoint) ? remoteClient : localClient;
    }

    /**
     * 动态切换当前使用的 Ollama endpoint。
     * <p>
     * 切换后，后续所有请求（包括聊天、模型查询）将使用新 endpoint。
     * 此操作是线程安全的。
     *
     * @param endpoint 目标 endpoint，必须为 "local" 或 "remote"
     * @throws IllegalArgumentException 如果 endpoint 无效
     */
    public void setActiveEndpoint(String endpoint) {
        if (endpoint == null || (!"local".equals(endpoint) && !"remote".equals(endpoint))) {
            throw new IllegalArgumentException("Endpoint must be 'local' or 'remote', got: " + endpoint);
        }
        if (!this.activeEndpoint.equals(endpoint)) {
            log.info("Switching Ollama endpoint from '{}' to '{}'", this.activeEndpoint, endpoint);
            this.activeEndpoint = endpoint;
        }
    }

    /**
     * 使用指定模型向当前激活的 Ollama 服务发起聊天请求。
     *
     * @param userMessage 用户输入的消息内容
     * @param model       模型名称（如 "qwen:latest", "llama3:8b"）
     * @return AI 生成的纯文本回复
     */
    public Mono<String> generateResponse(String userMessage, String model) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("User message cannot be null or empty"));
        }
        if (model == null || model.trim().isEmpty()) {
            model = "qwen:latest"; // 默认模型
        }

        OllamaChatRequest request = buildRequest(userMessage.trim(), model.trim());
        String finalModel = model;
        return getActiveClient()
                .chat(request)
                .map(OllamaChatResponse::getResponse)
                .doOnSuccess(response -> log.debug("Received response from {} using model {}", activeEndpoint, finalModel))
                .doOnError(error -> log.warn("Failed to get response from Ollama ({})", activeEndpoint, error));
    }

    /**
     * 查询当前激活 endpoint 下所有已拉取的模型列表。
     *
     * @return 模型名称列表（如 ["qwen:latest", "llama3:8b"]），若无模型则返回空列表
     */
    public Mono<List<String>> listModels() {
        return getActiveClient()
                .listModels()
                .onErrorReturn(Collections.emptyList())
                .doOnSuccess(models -> log.debug("Retrieved {} models from {}", models.size(), activeEndpoint));
    }

    /**
     * 构建 Ollama 聊天请求对象。
     * <p>
     * - 不启用流式响应（stream = false）
     * - 仅包含一条用户消息
     * - 不设置 keep_alive（使用 Ollama 默认）
     *
     * @param userMessage 用户消息内容
     * @param model       模型名称
     * @return 构建好的请求对象
     */
    private OllamaChatRequest buildRequest(String userMessage, String model) {
        OllamaChatRequest.Message message = new OllamaChatRequest.Message();
        message.setRole("user");
        message.setContent(userMessage);

        OllamaChatRequest request = new OllamaChatRequest();
        request.setModel(model);
        request.setMessages(Collections.singletonList(message));
        request.setStream(false); // 非流式，简化处理
        // keep_alive 保持默认（由 Ollama 服务决定）
        return request;
    }
}