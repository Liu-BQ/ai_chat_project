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
// TODO 魔法值待定义
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

    /**
     * 查询 Ollama 服务中所有已拉取（pulled）的本地模型列表。
     * <p>
     * 该方法调用 Ollama 的 {@code GET /api/tags} 接口，获取当前服务实例上可用的模型名称。
     * 返回结果仅包含模型的完整名称（如 {@code "qwen:latest"}、{@code "llama3:8b"}）。
     * </p>
     * <p>
     * <strong>注意</strong>：
     * <ul>
     *   <li>此接口 <strong>不返回云端未拉取的模型</strong>，仅列出已下载到本地的模型。</li>
     *   <li>若 Ollama 服务不可达或返回错误，建议调用方使用 {@code onErrorReturn} 或类似机制处理异常。</li>
     *   <li>路径 {@code "/api/tags"} 是 Ollama 官方 API 的固定端点，后续可考虑提取为常量以消除魔法值。</li>
     * </ul>
     * </p>
     *
     * @return 模型名称列表（按 Ollama 返回顺序），若无模型则返回空列表；发生网络或解析错误时将抛出异常
     * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#list-local-models">Ollama API - List Local Models</a>
     */
    public Mono<List<String>> listModels() {
        return webClient.get()
                .uri(OllamaConstants.ApiPath.TAGS)
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .map(response -> response.getModels().stream()
                        .map(OllamaModelsResponse.Model::getName)
                        .collect(Collectors.toList()));
    }
}