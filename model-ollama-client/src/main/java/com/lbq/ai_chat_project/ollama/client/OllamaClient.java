package com.lbq.ai_chat_project.ollama.client;

import com.lbq.ai_chat_project.ollama.dto.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.OllamaChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Ollama API 的 HTTP 客户端封装类。
 * <p>
 * 该组件通过 Spring 的 {@link WebClient}（响应式非阻塞 HTTP 客户端）与本地或远程的 Ollama 服务进行通信，
 * 封装了对 {@code /api/chat} 接口的调用，用于发送聊天请求并获取模型生成的响应。
 * </p>
 * <p>
 * 支持通过配置项 {@code ollama.base-url} 自定义 Ollama 服务地址（默认：{@code http://localhost:11434}）。
 * </p>
 * <p>
 * 此类被声明为 Spring Bean（通过 {@link Component} 注解），可直接注入到其他服务中使用。
 * </p>
 *
 * @author lbq
 * @since 1.0.0
 */
@Component
public class OllamaClient {

    private final WebClient webClient;

    public OllamaClient(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Mono<OllamaChatResponse> chat(OllamaChatRequest request) {
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class);
    }
}