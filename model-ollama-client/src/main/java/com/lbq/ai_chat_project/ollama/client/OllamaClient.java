package com.lbq.ai_chat_project.ollama.client;

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
import org.springframework.core.ParameterizedTypeReference;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Ollama API 底层HTTP客户端（完整版）
 * 基于 Spring WebFlux 实现响应式HTTP调用，支持Ollama所有核心API交互
 * 特点：
 * 1. 所有魔法值统一引用 OllamaConstants 常量，无硬编码
 * 2. 支持本地/远程Ollama服务连接，配置可通过 OllamaProperties 自定义
 * 3. 内置连接池、超时控制、日志打印、异常处理
 */
@Slf4j
public class OllamaClient {

    /** Spring WebFlux 响应式HTTP客户端核心实例 */
    private final WebClient webClient;

    /** Ollama服务基础地址（如：http://localhost:11434） */
    private final String baseUrl;

    /** JSON序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数：初始化Ollama客户端核心配置
     * @param endpoint Ollama服务端点配置（含基础地址、连接池、超时等参数）
     * @param objectMapper JSON序列化工具实例
     */
    public OllamaClient(@Nonnull OllamaProperties.OllamaEndpoint endpoint, @Nonnull ObjectMapper objectMapper) {
        // 校验入参非空
        Objects.requireNonNull(endpoint, "OllamaEndpoint配置不能为空");
        Objects.requireNonNull(endpoint.getBaseUrl(), "Ollama基础URL不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper不能为空");
        this.baseUrl = endpoint.getBaseUrl().trim();

        // 初始化Netty连接池（配置引用常量类）
        ConnectionProvider connectionProvider = ConnectionProvider.builder(OllamaConstants.Pool.CONNECTION_POOL_NAME)
                .maxConnections(endpoint.getConnectionPoolSize())
                .pendingAcquireTimeout(Duration.ofMillis(OllamaConstants.Pool.PENDING_ACQUIRE_TIMEOUT_MS))
                .maxIdleTime(Duration.ofMinutes(OllamaConstants.Pool.MAX_IDLE_TIME_MINUTES))
                .build();

        // 配置HttpClient：连接超时、长连接、读写超时
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, endpoint.getConnectTimeout())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(endpoint.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(endpoint.getWriteTimeout(), TimeUnit.MILLISECONDS))
                );

        // 构建WebClient：基础地址、默认请求头、日志过滤器
        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequestFilter())
                .filter(logResponseFilter())
                .build();

        // 初始化日志
        log.info("✅ OllamaClient初始化完成 | baseUrl: {} | 连接池大小: {} | 超时配置: 连接{}ms/读取{}ms/写入{}ms",
                this.baseUrl,
                endpoint.getConnectionPoolSize(),
                endpoint.getConnectTimeout(),
                endpoint.getReadTimeout(),
                endpoint.getWriteTimeout());
    }

    // ------------------------------ 核心业务方法 ------------------------------

    /**
     * 非流式聊天接口：同步获取完整的聊天回复
     * @param request 聊天请求参数（含模型名、用户消息、对话历史等）
     * @return 包含完整回复内容的响应对象（OllamaChatResponse）
     */
    @Nonnull
    public Mono<OllamaChatResponse> chat(@Nonnull OllamaChatRequest request) {
        request.setStream(false);
        log.debug("📤 发起非流式聊天请求 | 模型: {} | 用户消息: {}",
                request.getModel(),
                maskSensitiveInfo(request.getMessages().get(0).getContent()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.CHAT) // 引用API路径常量
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

    /**
     * 流式聊天接口：逐段获取聊天回复内容（适用于实时渲染）
     * @param request 聊天请求参数（含模型名、用户消息、对话历史等）
     * @return 流式返回的回复文本片段（String）
     */
    @Nonnull
    public Flux<String> streamChat(@Nonnull OllamaChatRequest request) {
        request.setStream(true);
        log.debug("📤 发起流式聊天请求 | 模型: {} | 用户消息: {}",
                request.getModel(),
                maskSensitiveInfo(request.getMessages().get(0).getContent()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.CHAT) // 引用API路径常量
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

    /**
     * 文本补全接口：基于提示词生成文本内容（支持流式/非流式）
     * @param request 文本补全请求参数（含模型名、提示词、流式开关等）
     * @return 流式返回补全文本片段，非流式返回完整JSON字符串
     */
    @Nonnull
    public Flux<String> generate(@Nonnull GenerateRequest request) {
        log.debug("📤 发起文本补全请求 | 模型: {} | 提示词: {}",
                request.getModel(),
                maskSensitiveInfo(request.getPrompt()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.GENERATE) // 引用API路径常量
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .map(jsonStr -> {
                    try {
                        // 解析JSON响应（字段名引用常量类）
                        Map<String, Object> responseMap = objectMapper.readValue(jsonStr, HashMap.class);
                        String response = (String) responseMap.get(OllamaConstants.JsonField.RESPONSE);
                        boolean done = Boolean.TRUE.equals(responseMap.getOrDefault(OllamaConstants.JsonField.DONE, false));
                        // 流式返回文本片段，非流式返回完整JSON
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
     * 生成嵌入向量接口：将文本转换为数值向量（适用于语义搜索、相似度计算）
     * @param request 嵌入生成请求参数（含模型名、输入文本等）
     * @return 文本对应的嵌入向量列表（List<List<Double>>）
     */
    @Nonnull
    public Mono<List<List<Double>>> embed(@Nonnull EmbedRequest request) {
        log.debug("📤 发起生成嵌入请求 | 模型: {} | 输入: {}",
                request.getModel(),
                maskSensitiveInfo(request.getInput().toString()));

        return webClient.post()
                .uri(OllamaConstants.ApiPath.EMBED) // 引用API路径常量
                .bodyValue(request)
                .retrieve()
                .bodyToMono(HashMap.class)
                .map(response -> (List<List<Double>>) response.get(OllamaConstants.JsonField.EMBEDDINGS)) // 引用JSON字段常量
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
     * 拉取模型接口：从Ollama仓库下载指定模型（流式返回进度）
     * @param request 模型拉取请求参数（含模型名、流式开关等）
     * @return 流式返回的拉取进度信息（百分比、状态描述）
     */
    @Nonnull
    public Flux<String> pullModel(@Nonnull ModelManageRequest request) {
        log.debug("📤 发起拉取模型请求 | 模型: {}", request.getModel());

        return webClient.post()
                .uri(OllamaConstants.ApiPath.PULL) // 引用API路径常量
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
     * 删除模型接口：从本地Ollama服务移除指定模型
     * @param modelName 要删除的模型名称（如 qwen:latest）
     * @return 删除操作结果（true=成功，false=失败）
     */
    @Nonnull
    public Mono<Boolean> deleteModel(@Nonnull String modelName) {
        log.debug("🗑️ 发起删除模型请求 | 模型: {}", modelName);

        return webClient.delete()
                .uri(uriBuilder -> uriBuilder.path(OllamaConstants.ApiPath.DELETE) // 引用API路径常量
                        .queryParam("model", modelName)
                        .build())
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(ignored -> log.debug("🗑️ 删除模型成功 | 模型: {}", modelName))
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
     * 复制模型接口：复制已存在的模型到新名称（快速创建自定义变体）
     * @param request 复制请求参数（含源模型名、目标模型名等）
     * @return 复制操作结果（true=成功，false=失败）
     */
    @Nonnull
    public Mono<Boolean> copyModel(@Nonnull ModelManageRequest request) {
        log.debug("📋 发起复制模型请求 | 源模型: {} | 目标模型: {}", request.getSource(), request.getDestination());

        return webClient.post()
                .uri(OllamaConstants.ApiPath.COPY) // 引用API路径常量
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(ignored -> log.debug("📋 复制模型成功 | 源模型: {} → 目标模型: {}",
                        request.getSource(), request.getDestination()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("复制模型API响应错误 | 状态码: %s | 源模型: %s | 目标模型: %s",
                            ex.getStatusCode(), request.getSource(), request.getDestination());
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("复制模型请求失败 | 源模型: %s | 目标模型: %s | 原因: %s",
                            request.getSource(), request.getDestination(), ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.just(false);
                });
    }

    /**
     * 创建自定义模型接口：基于基础模型创建新模型（支持系统提示、模板配置）
     * @param request 创建请求参数（含新模型名、基础模型名、系统提示等）
     * @return 流式返回的创建进度信息
     */
    @Nonnull
    public Flux<String> createModel(@Nonnull ModelManageRequest request) {
        log.debug("🛠️ 发起创建模型请求 | 新模型: {} | 基础模型: {}", request.getModel(), request.getFrom());

        return webClient.post()
                .uri(OllamaConstants.ApiPath.CREATE) // 引用API路径常量
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(progress -> log.debug("🛠️ 创建模型进度: {}", progress))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("创建模型API响应错误 | 状态码: %s | 新模型: %s",
                            ex.getStatusCode(), request.getModel());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("创建模型请求失败 | 新模型: %s | 原因: %s",
                            request.getModel(), ex.getMessage());
                    log.error(errorMsg, ex);
                    return Flux.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 查看模型详情接口（对应 /api/show）
     * 获取模型的完整配置信息（Modelfile、参数等）
     * 核心修改：HTTP 方法从 GET 改为 POST，参数通过请求体传递
     */
    @Nonnull
    public Mono<Map<String, Object>> showModel(@Nonnull String modelName) {
        log.debug("🔍 发起查看模型详情请求 | 模型: {}", modelName);

        // 构建请求体：Ollama /api/show 要求 POST 请求体包含 model 字段
        Map<String, String> requestBody = Collections.singletonMap("model", modelName);

        return webClient.post() // 关键修改：从 get() 改为 post()
                .uri(OllamaConstants.ApiPath.SHOW) // 仅保留接口路径，参数通过请求体传递
                .bodyValue(requestBody) // 传递请求体
                .retrieve()
                // 保持泛型类型明确，解决 Map → Map<String, Object> 类型不匹配
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnNext(detail -> log.debug("🔍 模型详情查询完成 | 模型: {} | 包含字段: {}",
                        modelName, detail.keySet()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("查看模型详情API响应错误 | 状态码: %s | 模型: %s | 响应体: %s",
                            ex.getStatusCode(), modelName, ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("查看模型详情请求失败 | 模型: %s | 原因: %s",
                            modelName, ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                });
    }

    /**
     * 列出运行中模型接口：获取当前内存中活跃的模型列表
     * @return 运行中模型列表（OllamaModelsResponse）
     */
    @Nonnull
    public Mono<OllamaModelsResponse> listRunningModels() {
        log.debug("🏃 发起查询运行中模型请求 | 服务地址: {}", this.baseUrl);

        return webClient.get()
                .uri(OllamaConstants.ApiPath.PS) // 引用API路径常量
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .doOnNext(response -> log.debug("🏃 运行中模型查询完成 | 模型数量: {}",
                        response.getModels() != null ? response.getModels().size() : 0))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    String errorMsg = String.format("查询运行中模型API响应错误 | 状态码: %s", ex.getStatusCode());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    String errorMsg = String.format("查询运行中模型请求失败 | 原因: %s", ex.getMessage());
                    log.error(errorMsg, ex);
                    return Mono.error(new RuntimeException(errorMsg, ex));
                });
    }

    // ------------------------------ 原有辅助方法 ------------------------------

    /**
     * 查询Ollama中已加载的模型列表（非运行中，已下载的模型）
     * @return 包含模型列表的响应对象（OllamaModelsResponse）
     */
    @Nonnull
    public Mono<OllamaModelsResponse> listModels() {
        log.debug("🔍 查询Ollama模型列表 | 服务地址: {}", this.baseUrl);

        return webClient.get()
                .uri(OllamaConstants.ApiPath.TAGS) // 引用API路径常量
                .retrieve()
                .bodyToMono(OllamaModelsResponse.class)
                .doOnNext(response -> log.debug("🔍 模型列表查询完成 | 模型数量: {}",
                        response.getModels() != null ? response.getModels().size() : 0))
                .onErrorReturn(new OllamaModelsResponse())
                .doOnError(ex -> log.error("模型列表查询失败 | 原因: {}", ex.getMessage(), ex));
    }

    /**
     * 检查Ollama服务是否正常运行（健康检查）
     * @return 服务运行状态（true=正常，false=异常）
     */
    @Nonnull
    public Mono<Boolean> isRunning() {
        log.trace("❤️ 执行Ollama服务健康检查 | 服务地址: {}", this.baseUrl);

        return webClient.get()
                .uri(OllamaConstants.ApiPath.TAGS) // 引用API路径常量
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .onErrorReturn(false)
                .doOnNext(healthy -> log.trace("❤️ 健康检查结果 | 服务地址: {} | 状态: {}",
                        this.baseUrl, healthy ? "正常" : "不可用"));
    }

    /**
     * 查询Ollama服务的版本信息
     * @return Ollama服务版本号（异常时返回"未知版本"）
     */
    @Nonnull
    public Mono<String> getVersion() {
        log.debug("📌 查询Ollama服务版本 | 服务地址: {}", this.baseUrl);

        return webClient.get()
                .uri(OllamaConstants.ApiPath.VERSION) // 引用API路径常量
                .retrieve()
                .bodyToMono(String.class)
                .map(version -> version.replace("\"", ""))
                .doOnNext(version -> log.debug("📌 Ollama版本查询完成 | 版本: {}", version))
                .onErrorReturn("未知版本")
                .doOnError(ex -> log.error("版本查询失败 | 原因: {}", ex.getMessage(), ex));
    }

    // ------------------------------ 私有工具方法 ------------------------------

    /**
     * 请求日志过滤器：记录HTTP请求的基础信息（方法、URL、请求头）
     * @return 日志过滤器实例（ExchangeFilterFunction）
     */
    private ExchangeFilterFunction logRequestFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.trace("[OllamaClient] 请求信息 | 方法: {} | URL: {} |  headers: {}",
                    clientRequest.method(),
                    clientRequest.url(),
                    clientRequest.headers());
            return Mono.just(clientRequest);
        });
    }

    /**
     * 响应日志过滤器：记录HTTP响应的状态码
     * @return 日志过滤器实例（ExchangeFilterFunction）
     */
    private ExchangeFilterFunction logResponseFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.trace("[OllamaClient] 响应信息 | 状态码: {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    /**
     * 解析流式聊天响应的JSON字符串为聊天响应对象
     * @param jsonStr 流式返回的JSON片段
     * @return 解析后的聊天响应对象（OllamaChatResponse）
     */
    private OllamaChatResponse parseStreamResponse(String jsonStr) {
        try {
            return objectMapper.readValue(jsonStr, OllamaChatResponse.class);
        } catch (Exception e) {
            log.error("解析流式响应失败 | 响应字符串: {}", jsonStr, e);
            return new OllamaChatResponse();
        }
    }

    /**
     * 脱敏处理敏感内容：避免日志中泄露完整长文本
     * @param content 需要脱敏的文本内容
     * @return 脱敏后的文本（超长截取前20字符+省略号）
     */
    private String maskSensitiveInfo(String content) {
        if (content == null) return "null";
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }

    /**
     * 获取Ollama服务基础地址
     * @return 基础地址字符串（如 http://localhost:11434）
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}