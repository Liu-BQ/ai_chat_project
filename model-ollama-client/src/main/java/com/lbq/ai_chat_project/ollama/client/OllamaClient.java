package com.lbq.ai_chat_project.ollama.client;

import com.lbq.ai_chat_project.ollama.config.OllamaProperties;
import com.lbq.ai_chat_project.ollama.constant.OllamaConstants;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatResponse;
import com.lbq.ai_chat_project.ollama.dto.model.OllamaModelsResponse;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Ollama API 的 HTTP 客户端封装类（无容错机制）。
 * <p>
 * 该组件通过 Spring 的 {@link WebClient} 与本地或远程的 Ollama 服务进行通信，
 * 封装了对 {@code /api/chat} 接口的调用，用于发送聊天请求并获取模型生成的响应。
 * </p>
 * <p>
 * ✅ 简洁高效：移除了 Resilience4j 依赖，减少运行时开销。
 * ✅ 线程安全：所有字段为 final，无共享可变状态。
 * ✅ 可配置：连接池、超时等参数通过 application.yml 动态调整。
 * </p>
 *
 * @author lbq
 * @since 1.0.0
 */
@Slf4j
public class OllamaClient {

    private final WebClient webClient;
    private final String baseUrl;

    /**
     * 构造函数：根据传入的 OllamaEndpoint 配置初始化 WebClient。
     * <p>
     * 此方法由 {@link com.lbq.ai_chat_project.ollama.config.OllamaClientConfig} 显式调用。
     * </p>
     *
     * @param endpoint Ollama 服务的连接配置
     */
    public OllamaClient(OllamaProperties.OllamaEndpoint endpoint) {
        this.baseUrl = endpoint.getBaseUrl();

        // 创建固定大小的 Netty 连接池，高效复用 TCP 连接
        ConnectionProvider provider = ConnectionProvider.create("ollama-pool", endpoint.getConnectionPoolSize());

        // 配置底层 HttpClient（基于 Netty）
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, endpoint.getConnectTimeout())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(endpoint.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(endpoint.getWriteTimeout(), TimeUnit.MILLISECONDS))
                );

        // 构建 WebClient
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequestFilter()) // 添加请求日志
                .build();

        log.info("OllamaClient initialized for base URL: {}, pool size: {}", baseUrl, endpoint.getConnectionPoolSize());
    }

    /**
     * 请求日志过滤器：记录每次发出的 HTTP 请求。
     *
     * @return ExchangeFilterFunction
     */
    private ExchangeFilterFunction logRequestFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("[OllamaClient] Sending request → {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    /**
     * 向 Ollama 服务的 /api/chat 接口发送聊天请求。
     * <p>
     * 流程：
     * 1. 发起 POST 请求
     * 2. 直接调用 Ollama 服务（无重试/熔断）
     * 3. 统一异常处理，转换为 RuntimeException
     * </p>
     *
     * @param request 聊天请求对象
     * @return 包含模型回复的响应流
     */
    public Mono<OllamaChatResponse> chat(OllamaChatRequest request) {
        log.debug("[OllamaClient] Preparing chat request to {} with model: {}", baseUrl, request.getModel());

        // 构建原始调用（无容错机制）
        Mono<OllamaChatResponse> responseMono = webClient.post()
                .uri(OllamaConstants.ApiPath.CHAT)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .doOnNext(response -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[OllamaClient] Received response from {}: model={}, done={}",
                                baseUrl, response.getModel(), response.isDone());
                    }
                });

        // 基础异常处理（无重试/熔断）
        return responseMono.onErrorResume(WebClientResponseException.class, ex -> {
            String errorMsg = String.format(
                    "Ollama API error: %s, status: %s, body: %s",
                    baseUrl, ex.getStatusCode(), ex.getResponseBodyAsString()
            );
            log.error(errorMsg);
            return Mono.error(new RuntimeException(errorMsg, ex));
        }).onErrorResume(Exception.class, ex -> {
            String errorMsg = String.format("Unexpected error calling Ollama at %s: %s", baseUrl, ex.getMessage());
            log.error(errorMsg, ex);
            return Mono.error(ex);
        });
    }

    // OllamaClient.java
    public Mono<List<String>> listModels() {
        return webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .map(response -> response.getModels().stream()
                        .map(OllamaModelsResponse.Model::getName)
                        .collect(Collectors.toList()));
    }
}