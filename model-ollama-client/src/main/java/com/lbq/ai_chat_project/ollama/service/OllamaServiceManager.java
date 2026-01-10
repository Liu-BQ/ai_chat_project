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
import org.springframework.cache.CacheManager;
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
 * Ollama服务管理器（核心服务封装类）
 * 功能定位：统一封装Ollama所有核心API，提供标准化服务接口，屏蔽本地/远程端点差异、缓存管理、参数校验等底层细节
 * 核心特性：
 * 1. 支持本地/远程Ollama服务端点动态切换
 * 2. 内置模型列表缓存（5分钟过期），减少重复查询开销
 * 3. 统一参数校验和异常处理，返回标准化业务异常
 * 4. 响应式编程风格，兼容Spring WebFlux异步场景
 * 核心修改：getActiveClient() 从 private → public，允许控制器层直接调用客户端基础方法
 */
@Slf4j
@Service
public class OllamaServiceManager {

    /** 本地Ollama客户端（连接本地Ollama服务） */
    private final OllamaClient localClient;

    /** 远程Ollama客户端（连接远程Ollama服务） */
    private final OllamaClient remoteClient;

    /** Ollama服务配置属性（本地/远程端点地址、超时、连接池等） */
    private final OllamaProperties ollamaProperties;

    /** 当前活跃的服务端点（local/remote，默认local） */
    @Getter
    private volatile String activeEndpoint = "local";

    /** 本地模型列表缓存（缓存key："models"，存储已拉取的本地模型列表） */
    private final Cache localModelCache;

    /** 远程模型列表缓存（缓存key："models"，存储已拉取的远程模型列表） */
    private final Cache remoteModelCache;

    /**
     * 构造函数：初始化客户端、缓存、配置
     * @param localClient 本地Ollama客户端（通过@Qualifier指定注入）
     * @param remoteClient 远程Ollama客户端（通过@Qualifier指定注入）
     * @param ollamaProperties Ollama服务配置属性
     * @throws NullPointerException 若任一依赖注入为null，直接抛出空指针异常
     */
    public OllamaServiceManager(
            @Qualifier("localOllamaClient") OllamaClient localClient,
            @Qualifier("remoteOllamaClient") OllamaClient remoteClient,
            OllamaProperties ollamaProperties,
            CacheManager cacheManager) { // 注入配置好的缓存管理器
        this.localClient = Objects.requireNonNull(localClient, "本地OllamaClient不能为空");
        this.remoteClient = Objects.requireNonNull(remoteClient, "远程OllamaClient不能为空");
        this.ollamaProperties = Objects.requireNonNull(ollamaProperties, "Ollama配置不能为空");

        // 从缓存管理器获取“5分钟过期”的缓存（缓存名要和配置对应）
        this.localModelCache = cacheManager.getCache("ollama-local-models");
        this.remoteModelCache = cacheManager.getCache("ollama-remote-models");

        // 校验缓存初始化成功
        Objects.requireNonNull(this.localModelCache, "本地模型缓存初始化失败");
        Objects.requireNonNull(this.remoteModelCache, "远程模型缓存初始化失败");

        log.info("✅ OllamaServiceManager初始化完成 | 默认激活端点: {} | 缓存初始化完成（5分钟过期）", activeEndpoint);
    }

    // ------------------------------ 核心端点管理方法 ------------------------------
    /**
     * 获取当前活跃的Ollama客户端（本地/远程）
     * 根据activeEndpoint动态返回对应客户端，保证所有API调用路由到当前激活的服务
     * @return 活跃的OllamaClient实例（local/remote）
     */
    public OllamaClient getActiveClient() {
        return "remote".equals(activeEndpoint) ? remoteClient : localClient;
    }

    /**
     * 切换Ollama服务端点（本地/远程）
     * @param endpoint 目标端点，仅支持"local"或"remote"
     * @throws IllegalArgumentException 若传入端点不是指定值，抛出参数非法异常
     */
    public void setActiveEndpoint(String endpoint) {
        // 校验端点合法性
        if (!"local".equals(endpoint) && !"remote".equals(endpoint)) {
            throw new IllegalArgumentException("端点必须是'local'或'remote'，当前传入: " + endpoint);
        }
        // 端点变更时，清理对应缓存（避免跨端点缓存污染）
        if (!this.activeEndpoint.equals(endpoint)) {
            log.info("🔄 切换Ollama服务端点 | 从'{}'切换到'{}'", this.activeEndpoint, endpoint);
            this.activeEndpoint = endpoint;
            getCacheByEndpoint(endpoint).clear();
        }
    }

    /**
     * 根据端点获取对应的模型缓存
     * @param endpoint 服务端点（local/remote）
     * @return 对应的缓存实例（localModelCache/remoteModelCache）
     */
    private Cache getCacheByEndpoint(String endpoint) {
        return "remote".equals(endpoint) ? remoteModelCache : localModelCache;
    }

    // ------------------------------ 基础聊天功能 ------------------------------
    /**
     * 非流式聊天：同步获取完整AI回复
     * 适用于短文本对话，无需实时渲染的场景
     * @param model 模型名称（如 qwen:latest、llama3:8b）
     * @param userMessage 用户输入消息（不能为空）
     * @return Mono< String >：AI生成的完整回复文本
     * @throws BaseException 若参数非法、模型返回空回复或调用失败，抛出业务异常
     */
    public Mono<String> chat(String model, String userMessage) {
        // 1. 参数校验（模型名、用户消息非空）
        validateChatParams(model, userMessage);
        // 2. 构建聊天请求（设置模型名、用户角色、消息内容）
        OllamaChatRequest request = buildChatRequest(model, userMessage);
        // 3. 调用活跃客户端的聊天接口，处理响应
        return getActiveClient().chat(request)
                // 提取AI回复内容（从响应对象中获取message.content）
                .map(OllamaChatResponse::getReplyContent)
                // 过滤空回复（若回复为null或空字符串，触发switchIfEmpty）
                .filter(reply -> reply != null && !reply.trim().isEmpty())
                // 空回复处理：抛出业务异常
                .switchIfEmpty(Mono.error(new BaseException("AI_REPLY_EMPTY", "模型返回空回复，请检查模型是否正常")))
                // 异常转换：统一包装为业务异常，携带错误码和消息
                .onErrorMap(ex -> new BaseException("CHAT_FAILED", "聊天请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 流式聊天：逐段获取AI回复（SSE格式）
     * 适用于长文本生成、实时渲染回复的场景（如前端打字机效果）
     * @param model 模型名称（如 qwen:latest、llama3:8b）
     * @param userMessage 用户输入消息（不能为空）
     * @return Flux< String >：流式返回的回复片段（每段为纯文本）
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Flux<String> streamChat(String model, String userMessage) {
        // 1. 参数校验（模型名、用户消息非空）
        validateChatParams(model, userMessage);
        // 2. 构建聊天请求（设置流式开关为true）
        OllamaChatRequest request = buildChatRequest(model, userMessage);
        // 3. 调用活跃客户端的流式聊天接口，异常统一包装
        return getActiveClient().streamChat(request)
                .onErrorMap(ex -> new BaseException("STREAM_CHAT_FAILED", "流式聊天请求失败: " + ex.getMessage(), ex));
    }

    // ------------------------------ 模型查询功能 ------------------------------
    /**
     * 查询当前端点已加载的模型名称列表（带缓存）
     * 缓存逻辑：首次查询从Ollama服务拉取，后续5分钟内直接从缓存获取
     * @return Mono< List< String > >：模型名称列表（如 ["qwen:latest", "all-minilm"]）
     */
    public Mono<List<String>> listModelNames() {
        // 1. 获取当前端点对应的缓存
        Cache activeCache = getCacheByEndpoint(activeEndpoint);
        // 2. 尝试从缓存获取模型列表
        Cache.ValueWrapper cacheWrapper = activeCache.get("models");
        List<OllamaModelsResponse.Model> models;

        if (cacheWrapper != null) {
            // 缓存命中：直接从缓存获取
            models = (List<OllamaModelsResponse.Model>) cacheWrapper.get();
            log.debug("📥 缓存命中 | 端点: {} | 模型数量: {}", activeEndpoint, models.size());
        } else {
            // 缓存未命中：从Ollama服务拉取并写入缓存
            models = loadModelsFromClient(activeEndpoint);
            log.debug("📥 缓存未命中 | 端点: {} | 加载模型数量: {}", activeEndpoint, models.size());
            activeCache.put("models", models);
        }

        // 3. 提取模型名称，转换为字符串列表返回
        List<String> modelNames = models.stream()
                .map(OllamaModelsResponse.Model::getName)
                .collect(Collectors.toList());
        return Mono.just(modelNames);
    }

    /**
     * 查询单个模型的基础详情（名称、修改时间、大小）
     * 从缓存中查询，无需重复调用Ollama服务
     * @param modelName 模型名称（不能为空）
     * @return Mono< OllamaModelsResponse.Model >：模型基础详情对象
     * @throws BaseException 若模型名称为空或模型不存在，抛出业务异常
     */
    public Mono<OllamaModelsResponse.Model> getModelDetail(String modelName) {
        // 1. 校验模型名称非空
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        // 2. 从缓存获取模型列表
        Cache activeCache = getCacheByEndpoint(activeEndpoint);
        Cache.ValueWrapper cacheWrapper = activeCache.get("models");
        List<OllamaModelsResponse.Model> models = cacheWrapper != null ?
                (List<OllamaModelsResponse.Model>) cacheWrapper.get() :
                Collections.emptyList();
        // 3. 查找目标模型，找到返回，未找到抛出异常
        return models.stream()
                .filter(model -> modelName.equals(model.getName()))
                .findFirst()
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new BaseException("MODEL_NOT_FOUND", "模型不存在: " + modelName)));
    }

    /**
     * 查询当前运行中的模型列表（内存中活跃模型）
     * @return Mono< OllamaModelsResponse >：运行中模型列表响应（含模型名称、修改时间、大小）
     * @throws BaseException 若查询失败，抛出业务异常
     */
    public Mono<OllamaModelsResponse> listRunningModels() {
        return getActiveClient().listRunningModels()
                .onErrorMap(ex -> new BaseException("LIST_RUNNING_MODELS_FAILED", "查询运行中模型失败: " + ex.getMessage(), ex));
    }

    // ------------------------------ 系统管理功能 ------------------------------
    /**
     * 服务健康检查：获取当前端点状态、版本、地址
     * @return Mono< HealthCheckResult >：健康检查结果（含端点、运行状态、版本、服务地址）
     */
    public Mono<HealthCheckResult> healthCheck() {
        OllamaClient activeClient = getActiveClient();
        // 并行调用两个接口：服务运行状态 + 服务版本
        return Mono.zip(
                activeClient.isRunning(),
                activeClient.getVersion()
        ).map(tuple -> new HealthCheckResult(
                activeEndpoint,          // 当前活跃端点
                tuple.getT1(),           // 服务运行状态（true=正常）
                tuple.getT2(),           // Ollama服务版本
                // 服务基础地址（根据端点切换）
                activeEndpoint.equals("local") ?
                        ollamaProperties.getLocal().getBaseUrl() :
                        ollamaProperties.getRemote().getBaseUrl()
        ));
    }

    // ------------------------------ 高级功能（文本补全、嵌入生成） ------------------------------
    /**
     * 文本补全：基于提示词生成文本（支持流式/非流式）
     * @param request 文本补全请求参数（含模型名、提示词、流式开关等）
     * @return Flux< String >：流式返回补全文本片段，非流式返回完整JSON字符串
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Flux<String> generate(GenerateRequest request) {
        validateGenerateParams(request);
        return getActiveClient().generate(request)
                .onErrorMap(ex -> new BaseException("GENERATE_FAILED", "文本补全请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 生成嵌入向量：将文本转换为数值向量（用于语义搜索、相似度计算）
     * @param request 嵌入生成请求参数（含模型名、输入文本等）
     * @return Mono< List< List< Double > > >：嵌入向量列表（每个输入文本对应一个向量）
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Mono<List<List<Double>>> embed(EmbedRequest request) {
        validateEmbedParams(request);
        return getActiveClient().embed(request)
                .onErrorMap(ex -> new BaseException("EMBED_FAILED", "生成嵌入请求失败: " + ex.getMessage(), ex));
    }

    // ------------------------------ 模型管理功能（拉取、删除、复制、创建） ------------------------------
    /**
     * 拉取模型：从Ollama仓库下载指定模型（流式返回进度）
     * @param request 模型拉取请求参数（含模型名、流式开关等）
     * @return Flux< String >：流式返回拉取进度（如百分比、下载状态）
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Flux<String> pullModel(ModelManageRequest request) {
        validatePullModelParams(request);
        return getActiveClient().pullModel(request)
                .onErrorMap(ex -> new BaseException("PULL_MODEL_FAILED", "拉取模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 删除模型：从当前端点删除指定模型
     * @param modelName 模型名称（不能为空）
     * @return Mono< Boolean >：删除结果（true=成功，false=失败）
     * @throws BaseException 若模型名称为空或调用失败，抛出业务异常
     */
    public Mono<Boolean> deleteModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        return getActiveClient().deleteModel(modelName)
                .onErrorMap(ex -> new BaseException("DELETE_MODEL_FAILED", "删除模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 复制模型：将源模型复制为新名称（快速创建自定义变体）
     * @param request 复制请求参数（含源模型名、目标模型名，均不能为空）
     * @return Mono< Boolean >：复制结果（true=成功，false=失败）
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Mono<Boolean> copyModel(ModelManageRequest request) {
        validateCopyModelParams(request);
        return getActiveClient().copyModel(request)
                .onErrorMap(ex -> new BaseException("COPY_MODEL_FAILED", "复制模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 创建自定义模型：基于基础模型创建新模型（支持系统提示、模板配置）
     * @param request 创建请求参数（含新模型名、基础模型名，均不能为空）
     * @return Flux< String >：流式返回创建进度（如配置生效、量化状态）
     * @throws BaseException 若参数非法或调用失败，抛出业务异常
     */
    public Flux<String> createModel(ModelManageRequest request) {
        validateCreateModelParams(request);
        return getActiveClient().createModel(request)
                .onErrorMap(ex -> new BaseException("CREATE_MODEL_FAILED", "创建模型请求失败: " + ex.getMessage(), ex));
    }

    /**
     * 查看模型详情：获取模型完整配置（Modelfile、量化类型、自定义参数等）
     * @param modelName 模型名称（不能为空）
     * @return Mono< Map< String, Object > >：模型完整配置（key为配置项，value为配置值）
     * @throws BaseException 若模型名称为空或调用失败，抛出业务异常
     */
    public Mono<Map<String, Object>> showModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        return getActiveClient().showModel(modelName)
                .onErrorMap(ex -> new BaseException("SHOW_MODEL_FAILED", "查看模型详情请求失败: " + ex.getMessage(), ex));
    }

    // ------------------------------ 参数校验工具方法 ------------------------------
    /**
     * 校验文本补全请求参数
     * @param request 文本补全请求对象
     * @throws BaseException 若模型名或提示词为空，抛出业务异常
     */
    private void validateGenerateParams(GenerateRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new BaseException("PROMPT_EMPTY", "提示词不能为空");
        }
    }

    /**
     * 校验嵌入生成请求参数
     * @param request 嵌入生成请求对象
     * @throws BaseException 若模型名为空或输入文本为空，抛出业务异常
     */
    private void validateEmbedParams(EmbedRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (request.getInput() == null) {
            throw new BaseException("INPUT_EMPTY", "输入文本不能为空");
        }
        // 校验输入文本非空（支持String或List<String>类型）
        if ((request.getInput() instanceof List && ((List<?>) request.getInput()).isEmpty())
                || (request.getInput() instanceof String && ((String) request.getInput()).trim().isEmpty())) {
            throw new BaseException("INPUT_EMPTY", "输入文本不能为空");
        }
    }

    /**
     * 校验模型拉取请求参数
     * @param request 模型拉取请求对象
     * @throws BaseException 若模型名为空，抛出业务异常
     */
    private void validatePullModelParams(ModelManageRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
    }

    /**
     * 校验模型复制请求参数
     * @param request 模型复制请求对象
     * @throws BaseException 若源模型名或目标模型名为空，抛出业务异常
     */
    private void validateCopyModelParams(ModelManageRequest request) {
        if (request.getSource() == null || request.getSource().trim().isEmpty()) {
            throw new BaseException("SOURCE_MODEL_EMPTY", "源模型名称不能为空");
        }
        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            throw new BaseException("DESTINATION_MODEL_EMPTY", "目标模型名称不能为空");
        }
    }

    /**
     * 校验自定义模型创建请求参数
     * @param request 模型创建请求对象
     * @throws BaseException 若新模型名或基础模型名为空，抛出业务异常
     */
    private void validateCreateModelParams(ModelManageRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "新模型名称不能为空");
        }
        if (request.getFrom() == null || request.getFrom().trim().isEmpty()) {
            throw new BaseException("BASE_MODEL_EMPTY", "基础模型名称不能为空");
        }
    }

    /**
     * 校验聊天请求参数
     * @param model 模型名称
     * @param userMessage 用户消息
     * @throws BaseException 若模型名或用户消息为空，抛出业务异常
     */
    private void validateChatParams(String model, String userMessage) {
        if (model == null || model.trim().isEmpty()) {
            throw new BaseException("MODEL_NAME_EMPTY", "模型名称不能为空");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new BaseException("USER_MESSAGE_EMPTY", "用户消息不能为空");
        }
    }

    // ------------------------------ 工具方法 ------------------------------
    /**
     * 构建聊天请求对象
     * @param model 模型名称
     * @param userMessage 用户消息内容
     * @return OllamaChatRequest：构建完成的聊天请求（用户角色为"user"）
     */
    private OllamaChatRequest buildChatRequest(String model, String userMessage) {
        OllamaChatRequest request = new OllamaChatRequest();
        request.setModel(model.trim()); // 设置模型名（去除首尾空格）
        // 构建用户消息（角色为user，内容为用户输入）
        OllamaChatRequest.Message message = new OllamaChatRequest.Message();
        message.setRole("user");
        message.setContent(userMessage.trim());
        // 设置消息列表（单次聊天，仅包含当前用户消息）
        request.setMessages(Collections.singletonList(message));
        return request;
    }

    /**
     * 从指定端点拉取模型列表（缓存未命中时调用）
     * @param endpoint 服务端点（local/remote）
     * @return List< OllamaModelsResponse.Model >：模型列表（为空则返回空列表）
     */
    private List<OllamaModelsResponse.Model> loadModelsFromClient(String endpoint) {
        log.debug("📥 加载{}端点模型列表", endpoint);
        // 调用对应客户端的listModels接口，阻塞10秒超时（避免无限等待）
        OllamaModelsResponse response = ("remote".equals(endpoint) ? remoteClient : localClient)
                .listModels()
                .block(Duration.ofSeconds(10));
        // 响应为空或模型列表为空时，返回空列表
        return response != null && response.getModels() != null ? response.getModels() : Collections.emptyList();
    }

    /**
     * 健康检查结果记录类（封装端点状态、版本、地址）
     * @param activeEndpoint 当前活跃端点（local/remote）
     * @param isRunning 服务运行状态（true=正常，false=异常）
     * @param ollamaVersion Ollama服务版本号（如 "0.1.30"）
     * @param baseUrl 服务基础地址（如 "http://localhost:11434"）
     */
    public record HealthCheckResult(
            String activeEndpoint,
            boolean isRunning,
            String ollamaVersion,
            String baseUrl
    ) {}
}