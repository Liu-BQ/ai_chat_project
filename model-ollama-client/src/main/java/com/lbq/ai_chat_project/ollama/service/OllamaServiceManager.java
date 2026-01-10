package com.lbq.ai_chat_project.ollama.service;

import com.lbq.ai_chat_project.common.exception.BaseException;
import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import com.lbq.ai_chat_project.ollama.config.OllamaProperties;
import com.lbq.ai_chat_project.ollama.dto.chat.EmbedRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.GenerateRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.OllamaChatResponse;
import com.lbq.ai_chat_project.ollama.dto.model.ModelManageRequest;
import com.lbq.ai_chat_project.ollama.dto.model.OllamaModelsResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ollama服务管理器（完整版，封装所有核心API）
 */
@Slf4j
@Service
public class OllamaServiceManager {
    private final OllamaClient localClient;
    private final OllamaClient remoteClient;
    private final OllamaProperties ollamaProperties;
    @Getter
    private volatile String activeEndpoint = "local";
    private final Cache localModelCache;
    private final Cache remoteModelCache;

    public OllamaServiceManager(
            @Qualifier("localOllamaClient") OllamaClient localClient,
            @Qualifier("remoteOllamaClient") OllamaClient remoteClient,
            OllamaProperties ollamaProperties) {
        this.localClient = Objects.requireNonNull(localClient, "本地OllamaClient不能为空");
        this.remoteClient = Objects.requireNonNull(remoteClient, "远程OllamaClient不能为空");
        this.ollamaProperties = Objects.requireNonNull(ollamaProperties, "Ollama配置不能为空");
        this.localModelCache = new ConcurrentMapCache("ollama-local-models", true);
        this.remoteModelCache = new ConcurrentMapCache("ollama-remote-models", true);
        log.info("✅ OllamaServiceManager初始化完成 | 默认激活端点: {} | 缓存初始化完成（5分钟过期）", activeEndpoint);
    }

    // ------------------------------ 已有方法（保持不变） ------------------------------
    public void setActiveEndpoint(String endpoint) {
        if (!"local".equals(endpoint) && !"remote".equals(endpoint)) {
            throw new IllegalArgumentException("端点必须是'local'或'remote'，当前传入: " + endpoint);
        }
        if (!this.activeEndpoint.equals(endpoint)) {
            log.info("🔄 切换Ollama服务端点 | 从'{}'切换到'{}'", this.activeEndpoint, endpoint);
            this.activeEndpoint = endpoint;
            getCacheByEndpoint(endpoint).clear();
        }
    }

    private Cache getCacheByEndpoint(String endpoint) {
        return "remote".equals(endpoint) ? remoteModelCache : localModelCache;
    }

    private OllamaClient getActiveClient() {
        return "remote".equals(activeEndpoint) ? remoteClient : localClient;
    }

    public Mono<String> chat(String model, String userMessage) {
        validateChatParams(model, userMessage);
        OllamaChatRequest request = buildChatRequest(model, userMessage);
        return getActiveClient().chat(request)
                .map(OllamaChatResponse::getReplyContent)
                .filter(reply -> reply != null && !reply.trim().isEmpty())
                .switchIfEmpty(Mono.error(new BaseException("AI_REPLY_EMPTY", "模型返回空回复，请检查模型是否正常")))
                .onErrorMap(ex -> new BaseException("CHAT_FAILED", "聊天请求失败: " + ex.getMessage(), ex));
    }

    public Flux<String> streamChat(String model, String userMessage) {
        validateChatParams(model, userMessage);
        OllamaChatRequest request = buildChatRequest(model, userMessage);
        return getActiveClient().streamChat(request)
                .onErrorMap(ex -> new BaseException("STREAM_CHAT_FAILED", "流式聊天请求失败: " + ex.getMessage(), ex));
    }

    public Mono<List<String>> listModelNames() {
        Cache activeCache = getCacheByEndpoint(activeEndpoint);
        Cache.ValueWrapper cacheWrapper = activeCache.get("models");
        List<OllamaModelsResponse.Model> models;
        if (cacheWrapper != null) {
            models = (List<OllamaModelsResponse.Model>) cacheWrapper.get();
            log.debug("📥 缓存命中 | 端点: {} | 模型数量: {}", activeEndpoint, models.size());
        } else {
            models = loadModelsFromClient(activeEndpoint);
            log.debug("📥 缓存未命中 | 端点: {} | 加载模型数量: {}", activeEndpoint, models.size());
            activeCache.put("models", models);
        }
        List<String> modelNames = models.stream()
                .map(OllamaModelsResponse.Model::getName)
                .collect(Collectors.toList());
        return Mono.just(modelNames);
    }

    public Mono<OllamaModelsResponse.Model> getModelDetail(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        Cache activeCache = getCacheByEndpoint(activeEndpoint);
        Cache.ValueWrapper cacheWrapper = activeCache.get("models");
        List<OllamaModelsResponse.Model> models = cacheWrapper != null ?
                (List<OllamaModelsResponse.Model>) cacheWrapper.get() :
                Collections.emptyList();
        return models.stream()
                .filter(model -> modelName.equals(model.getName()))
                .findFirst()
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new BaseException("MODEL_NOT_FOUND", "模型不存在: " + modelName)));
    }

    public Mono<HealthCheckResult> healthCheck() {
        OllamaClient activeClient = getActiveClient();
        return Mono.zip(
                activeClient.isRunning(),
                activeClient.getVersion()
        ).map(tuple -> new HealthCheckResult(
                activeEndpoint,
                tuple.getT1(),
                tuple.getT2(),
                activeEndpoint.equals("local") ?
                        ollamaProperties.getLocal().getBaseUrl() :
                        ollamaProperties.getRemote().getBaseUrl()
        ));
    }

    // ------------------------------ 新增封装方法 ------------------------------
    /**
     * 文本补全（封装 /api/generate）
     */
    public Flux<String> generate(GenerateRequest request) {
        validateGenerateParams(request);
        return getActiveClient().generate(request)
                .onErrorMap(ex -> new BaseException("GENERATE_FAILED", "文本补全请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 生成嵌入向量（封装 /api/embed）
     */
    public Mono<List<List<Double>>> embed(EmbedRequest request) {
        validateEmbedParams(request);
        return getActiveClient().embed(request)
                .onErrorMap(ex -> new BaseException("EMBED_FAILED", "生成嵌入请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 拉取模型（封装 /api/pull）
     */
    public Flux<String> pullModel(ModelManageRequest request) {
        validatePullModelParams(request);
        return getActiveClient().pullModel(request)
                .onErrorMap(ex -> new BaseException("PULL_MODEL_FAILED", "拉取模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 删除模型（封装 /api/delete）
     */
    public Mono<Boolean> deleteModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        return getActiveClient().deleteModel(modelName)
                .onErrorMap(ex -> new BaseException("DELETE_MODEL_FAILED", "删除模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 复制模型（封装 /api/copy）
     */
    public Mono<Boolean> copyModel(ModelManageRequest request) {
        validateCopyModelParams(request);
        return getActiveClient().copyModel(request)
                .onErrorMap(ex -> new BaseException("COPY_MODEL_FAILED", "复制模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 创建自定义模型（封装 /api/create）
     */
    public Flux<String> createModel(ModelManageRequest request) {
        validateCreateModelParams(request);
        return getActiveClient().createModel(request)
                .onErrorMap(ex -> new BaseException("CREATE_MODEL_FAILED", "创建模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 查看模型详情（封装 /api/show）
     */
    public Mono<Map<String, Object>> showModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        return getActiveClient().showModel(modelName)
                .onErrorMap(ex -> new BaseException("SHOW_MODEL_FAILED", "查看模型详情请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 列出运行中模型（封装 /api/ps）
     */
    public Mono<OllamaModelsResponse> listRunningModels() {
        return getActiveClient().listRunningModels()
                .onErrorMap(ex -> new BaseException("LIST_RUNNING_MODELS_FAILED", "查询运行中模型失败: " + ex.getMessage(), ex));
    }

    // ------------------------------ 参数校验工具方法 ------------------------------
    private void validateGenerateParams(GenerateRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new BaseException("PROMPT_EMPTY", "提示词不能为空");
        }
    }

    private void validateEmbedParams(EmbedRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (request.getInput() == null) {
            throw new BaseException("INPUT_EMPTY", "输入文本不能为空");
        }
        if ((request.getInput() instanceof List && ((List<?>) request.getInput()).isEmpty())
                || (request.getInput() instanceof String && ((String) request.getInput()).trim().isEmpty())) {
            throw new BaseException("INPUT_EMPTY", "输入文本不能为空");
        }
    }

    private void validatePullModelParams(ModelManageRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
    }

    private void validateCopyModelParams(ModelManageRequest request) {
        if (request.getSource() == null || request.getSource().trim().isEmpty()) {
            throw new BaseException("SOURCE_MODEL_EMPTY", "源模型名称不能为空");
        }
        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            throw new BaseException("DESTINATION_MODEL_EMPTY", "目标模型名称不能为空");
        }
    }

    private void validateCreateModelParams(ModelManageRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "新模型名称不能为空");
        }
        if (request.getFrom() == null || request.getFrom().trim().isEmpty()) {
            throw new BaseException("BASE_MODEL_EMPTY", "基础模型名称不能为空");
        }
    }

    private void validateChatParams(String model, String userMessage) {
        if (model == null || model.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new BaseException("USER_MESSAGE_EMPTY", "用户消息不能为空");
        }
    }

    private OllamaChatRequest buildChatRequest(String model, String userMessage) {
        OllamaChatRequest request = new OllamaChatRequest();
        request.setModel(model.trim());
        OllamaChatRequest.Message message = new OllamaChatRequest.Message();
        message.setRole("user");
        message.setContent(userMessage.trim());
        request.setMessages(Collections.singletonList(message));
        return request;
    }

    private List<OllamaModelsResponse.Model> loadModelsFromClient(String endpoint) {
        log.debug("📥 加载{}端点模型列表", endpoint);
        OllamaModelsResponse response = ("remote".equals(endpoint) ? remoteClient : localClient)
                .listModels()
                .block(Duration.ofSeconds(10));
        return response != null && response.getModels() != null ? response.getModels() : Collections.emptyList();
    }

    public record HealthCheckResult(
            String activeEndpoint,
            boolean isRunning,
            String ollamaVersion,
            String baseUrl
    ) {}
}