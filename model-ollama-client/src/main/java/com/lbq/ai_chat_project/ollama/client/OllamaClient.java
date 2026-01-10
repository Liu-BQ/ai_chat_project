package com.lbq.ai_chat_project.ollama.client;

import tools.jackson.databind.ObjectMapper;
import com.lbq.ai_chat_project.ollama.config.OllamaProperties;
import com.lbq.ai_chat_project.ollama.constant.OllamaConstants;
import com.lbq.ai_chat_project.ollama.dto.chat.EmbedRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.GenerateRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatResponse;
import com.lbq.ai_chat_project.ollama.dto.model.ModelManageRequest;
import com.lbq.ai_chat_project.ollama.dto.model.OllamaModelsResponse;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Ollama API 底层HTTP客户端（完整版，支持所有核心API）
 */
@Slf4j
public class OllamaClient {
    private final WebClient webClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OllamaClient(@Nonnull OllamaProperties.OllamaEndpoint endpoint, @Nonnull ObjectMapper objectMapper) {
        Objects.requireNonNull(endpoint, "OllamaEndpoint配置不能为空");
        Objects.requireNonNull(endpoint.getBaseUrl(), "Ollama基础URL不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper不能为空（Spring会自动注入）");
        this.baseUrl = endpoint.getBaseUrl().trim();

        // 初始化Netty连接池
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ollama-http-pool")
                .maxConnections(endpoint.getConnectionPoolSize())
                .pendingAcquireTimeout(Duration.ofMillis(3000))
                .maxIdleTime(Duration.ofMinutes(5))
                .build();

        // 配置HttpClient
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, endpoint.getConnectTimeout())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(endpoint.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(endpoint.getWriteTimeout(), TimeUnit.MILLISECONDS))
                );

        // 构建WebClient
        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequestFilter())
                .filter(logResponseFilter())
                .build();

        log.info("✅ OllamaClient初始化完成 | baseUrl: {} | 连接池大小: {} | 超时配置: 连接{}ms/读取{}ms/写入{}ms",
                this.baseUrl,
                endpoint.getConnectionPoolSize(),
                endpoint.getConnectTimeout(),
                endpoint.getReadTimeout(),
                endpoint.getWriteTimeout());
    }

    // ------------------------------ 已有方法（保持不变） ------------------------------
    @Nonnull
    public Mono<OllamaChatResponse> chat(@Nonnull OllamaChatRequest request) {
        request.setStream(false);
        log.debug("📤 发起非流式聊天请求 | 模型: {} | 用户消息: {}",
                request.getModel(),
                maskSensitiveInfo(request.getMessages().get(0).getContent()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.CHAT)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .doOnNext(response -> log.debug("📥 接收非流式响应 | 模型: {} | 完成状态: {} | 回复: {}",
                        response.getModel(),
                        response.isDone(),
                        maskSensitiveInfo(response.getReplyContent())))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("Ollama API响应错误 | 状态码: %s | 响应体: %s",
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("非流式聊天请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                });
    }

    @Nonnull
    public Flux<String> streamChat(@Nonnull OllamaChatRequest request) {
        request.setStream(true);
        log.debug("📤 发起流式聊天请求 | 模型: {} | 用户消息: {}",
                request.getModel(),
                maskSensitiveInfo(request.getMessages().get(0).getContent()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.CHAT)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::parseStreamResponse)
                .filter(response -> response.isDone() && response.getReplyContent() != null)
                .map(OllamaChatResponse::getReplyContent)
                .doOnNext(reply -> log.trace("📥 接收流式响应片段 | 回复: {}", maskSensitiveInfo(reply)))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("Ollama API流式响应错误 | 状态码: %s | 响应体: %s",
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("流式聊天请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                });
    }

    @Nonnull
    public Mono<OllamaModelsResponse> listModels() {
        log.debug("🔍 查询Ollama模型列表 | 服务地址: {}", this.baseUrl);
        return webClient.get()
                .uri(OllamaConstants.ApiPath.TAGS)
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .doOnNext(response -> log.debug("🔍 模型列表查询完成 | 模型数量: {}",
                        response.getModels() != null ? response.getModels().size() : 0))
                .onErrorReturn(new OllamaModelsResponse())
                .doOnError(ex -> log.error("模型列表查询失败 | 原因: {}", ex.getMessage(), ex));
    }

    @Nonnull
    public Mono<Boolean> isRunning() {
        log.trace("❤️ 执行Ollama服务健康检查 | 服务地址: {}", this.baseUrl);
        return webClient.get()
                .uri(OllamaConstants.ApiPath.TAGS)
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .onErrorReturn(false)
                .doOnNext(healthy -> log.trace("❤️ 健康检查结果 | 服务地址: {} | 状态: {}",
                        this.baseUrl, healthy ? "正常" : "不可用"));
    }

    @Nonnull
    public Mono<String> getVersion() {
        log.debug("📌 查询Ollama服务版本 | 服务地址: {}", this.baseUrl);
        return webClient.get()
                .uri("/version")
                .retrieve()
                .bodyToMono(String.class)
                .map(version -> version.replace("\"", ""))
                .doOnNext(version -> log.debug("📌 Ollama版本查询完成 | 版本: {}", version))
                .onErrorReturn("未知版本")
                .doOnError(ex -> log.error("版本查询失败 | 原因: {}", ex.getMessage(), ex));
    }

    // ------------------------------ 新增核心API方法 ------------------------------
    /**
     * 文本补全（对应 /api/generate 接口）
     * @param request 文本补全请求参数
     * @return 流式响应返回Flux<String>，非流式返回Mono<String>
     */
    @Nonnull
    public Flux<String> generate(@Nonnull GenerateRequest request) {
        log.debug("📤 发起文本补全请求 | 模型: {} | 提示词: {}",
                request.getModel(),
                maskSensitiveInfo(request.getPrompt()));

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .map(jsonStr -> {
                    try {
                        Map<String, Object> responseMap = objectMapper.readValue(jsonStr, Map.class);
                        String response = (String) responseMap.get("response");
                        boolean done = (Boolean) responseMap.getOrDefault("done", false);
                        // 流式返回时，仅返回非空响应内容；非流式返回完整结果
                        return (request.isStream() && response != null) ? response : jsonStr;
                    } catch (Exception e) {
                        log.error("解析文本补全响应失败 | 响应字符串: {}", jsonStr, e);
                        return "";
                    }
                })
                .filter(content -> !content.isEmpty())
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("文本补全API响应错误 | 状态码: %s | 响应体: %s",
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("文本补全请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 生成嵌入向量（对应 /api/embed 接口）
     * @param request 生成嵌入请求参数
     * @return 嵌入向量列表
     */
    @Nonnull
    public Mono<List<List<Double>>> embed(@Nonnull EmbedRequest request) {
        log.debug("📤 发起生成嵌入请求 | 模型: {} | 输入: {}",
                request.getModel(),
                maskSensitiveInfo(request.getInput().toString()));

        return webClient.post()
                .uri("/api/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (List<List<Double>>) response.get("embeddings"))
                .doOnNext(embeddings -> log.debug("📥 生成嵌入完成 | 向量数量: {}", embeddings.size()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("生成嵌入API响应错误 | 状态码: %s | 响应体: %s",
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("生成嵌入请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 拉取模型（对应 /api/pull 接口）
     * @param request 拉取模型请求参数
     * @return 流式进度信息
     */
    @Nonnull
    public Flux<String> pullModel(@Nonnull ModelManageRequest request) {
        log.debug("📤 发起拉取模型请求 | 模型: {}", request.getModel());

        return webClient.post()
                .uri("/api/pull")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(progress -> log.debug("📥 拉取模型进度: {}", progress))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("拉取模型API响应错误 | 状态码: %s | 响应体: %s",
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("拉取模型请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 删除模型（对应 /api/delete 接口）
     * @param modelName 模型名称
     * @return 操作结果（true-成功，false-失败）
     */
    @Nonnull
    public Mono<Boolean> deleteModel(@Nonnull String modelName) {
        log.debug("🗑️ 发起删除模型请求 | 模型: {}", modelName);

        return webClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/api/delete").queryParam("model", modelName).build())
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(_ -> log.debug("🗑️ 删除模型成功 | 模型: {}", modelName))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("删除模型API响应错误 | 状态码: %s | 模型: %s",
                            ex.getStatusCode(), modelName);
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("删除模型请求失败 | 模型: %s | 原因: %s", modelName, ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                });
    }

    /**
     * 复制模型（对应 /api/copy 接口）
     * @param request 复制模型请求参数（source-源模型，destination-目标模型）
     * @return 操作结果（true-成功，false-失败）
     */
    @Nonnull
    public Mono<Boolean> copyModel(@Nonnull ModelManageRequest request) {
        log.debug("📋 发起复制模型请求 | 源模型: {} | 目标模型: {}", request.getSource(), request.getDestination());

        Map<String, String> copyParams = Map.of(
                "source", request.getSource(),
                "destination", request.getDestination()
        );

        return webClient.post()
                .uri("/api/copy")
                .bodyValue(copyParams)
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(_ -> log.debug("📋 复制模型成功 | 源模型: {} → 目标模型: {}", request.getSource(), request.getDestination()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("复制模型API响应错误 | 状态码: %s | 源模型: %s",
                            ex.getStatusCode(), request.getSource());
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("复制模型请求失败 | 源模型: %s | 原因: %s", request.getSource(), ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                });
    }

    /**
     * 创建自定义模型（对应 /api/create 接口）
     * @param request 创建模型请求参数
     * @return 流式进度信息
     */
    @Nonnull
    public Flux<String> createModel(@Nonnull ModelManageRequest request) {
        log.debug("✨ 发起创建模型请求 | 新模型: {} | 基础模型: {}", request.getModel(), request.getFrom());

        return webClient.post()
                .uri("/api/create")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(progress -> log.debug("✨ 创建模型进度: {}", progress))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("创建模型API响应错误 | 状态码: %s | 新模型: %s",
                            ex.getStatusCode(), request.getModel());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("创建模型请求失败 | 新模型: %s | 原因: %s", request.getModel(), ex.getMessage());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 查看模型详情（对应 /api/show 接口）
     * @param modelName 模型名称
     * @return 模型详情（Map格式，包含modelfile、parameters等）
     */
    @Nonnull
    public Mono<Map<String, Object>> showModel(@Nonnull String modelName) {
        log.debug("🔍 发起查看模型详情请求 | 模型: {}", modelName);

        Map<String, String> showParams = Map.of("model", modelName);

        return webClient.post()
                .uri("/api/show")
                .bodyValue(showParams)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(detail -> log.debug("🔍 查看模型详情完成 | 模型: {}", modelName))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("查看模型详情API响应错误 | 状态码: %s | 模型: %s",
                            ex.getStatusCode(), modelName);
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("查看模型详情请求失败 | 模型: %s | 原因: %s", modelName, ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 列出运行中模型（对应 /api/ps 接口）
     * @return 运行中模型列表
     */
    @Nonnull
    public Mono<OllamaModelsResponse> listRunningModels() {
        log.debug("🔍 查询运行中模型列表 | 服务地址: {}", this.baseUrl);

        return webClient.get()
                .uri("/api/ps")
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .doOnNext(response -> log.debug("🔍 运行中模型查询完成 | 模型数量: {}",
                        response.getModels() != null ? response.getModels().size() : 0))
                .onErrorReturn(new OllamaModelsResponse())
                .doOnError(ex -> log.error("运行中模型查询失败 | 原因: {}", ex.getMessage(), ex));
    }

    // ------------------------------ 私有工具方法 ------------------------------
    private ExchangeFilterFunction logRequestFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.trace("[OllamaClient] 请求信息 | 方法: {} | URL: {} |  headers: {}",
                    clientRequest.method(),
                    clientRequest.url(),
                    clientRequest.headers());
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponseFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.trace("[OllamaClient] 响应信息 | 状态码: {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    private OllamaChatResponse parseStreamResponse(String jsonStr) {
        try {
            return objectMapper.readValue(jsonStr, OllamaChatResponse.class);
        } catch (Exception e) {
            log.error("解析流式响应失败 | 响应字符串: {}", jsonStr, e);
            return new OllamaChatResponse();
        }
    }

    private String maskSensitiveInfo(String content) {
        if (content == null) return "null";
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}