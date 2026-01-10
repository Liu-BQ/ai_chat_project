package com.lbq.ai_chat_project.web.api;

import com.lbq.ai_chat_project.common.exception.BaseException;
import com.lbq.ai_chat_project.common.result.BaseResult;
import com.lbq.ai_chat_project.ollama.dto.chat.EmbedRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.GenerateRequest;
import com.lbq.ai_chat_project.ollama.dto.model.ModelManageRequest;
import com.lbq.ai_chat_project.ollama.dto.model.OllamaModelsResponse;
import com.lbq.ai_chat_project.ollama.service.OllamaServiceManager;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Ollama前端API控制器（完整版，支持所有核心接口）
 * 优化点：
 * 1. 修复所有返回类型不匹配问题
 * 2. 规范异常处理链顺序
 * 3. 移除Mono嵌套包装
 * 4. 补充接口文档注释，优化参数校验
 */
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OllamaController {

    private final OllamaServiceManager ollamaServiceManager;

    // ------------------------------ 基础聊天接口 ------------------------------
    /**
     * 非流式聊天（同步获取完整回复）
     * @param model 模型名称（如 qwen:latest）
     * @param message 用户消息内容
     * @return 完整聊天回复
     */
    @PostMapping("/chat")
    public Mono<BaseResult<String>> chat(
            @RequestParam @NotBlank(message = "模型名称不能为空") String model,
            @RequestParam @NotBlank(message = "用户消息不能为空") String message) {
        log.info("📩 接收非流式聊天请求 | 模型: {} | 用户消息: {}", model, maskMessage(message));
        // 关键：设置接口超时为 5分钟，覆盖全局默认值
        return ollamaServiceManager.chat(model, message)
                .map(BaseResult::success)
                .timeout(Duration.ofMinutes(5))  // 手动设置超时
                .onErrorResume(TimeoutException.class, ex ->
                        Mono.just(BaseResult.error("请求处理超时，请简化提问或稍后重试")))
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("聊天失败: " + ex.getMessage())));
    }

    /**
     * 流式聊天（逐段返回回复，SSE格式，适用于前端实时渲染）
     * @param model 模型名称
     * @param message 用户消息内容
     * @return 流式回复片段（SSE格式：data: 内容\n\n）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam @NotBlank(message = "模型名称不能为空") String model,
            @RequestParam @NotBlank(message = "用户消息不能为空") String message) {
        log.info("📩 接收流式聊天请求 | 模型: {} | 用户消息: {}", model, maskMessage(message));
        return ollamaServiceManager.streamChat(model, message)
                .map(reply -> "data: " + reply + "\n\n")
                .onErrorResume(BaseException.class, ex -> Flux.just("data: 错误: " + ex.getMessage() + "\n\n"))
                .onErrorResume(ex -> Flux.just("data: 流式聊天失败: " + ex.getMessage() + "\n\n"));
    }

    // ------------------------------ 模型查询接口 ------------------------------
    /**
     * 查询所有已加载模型名称列表
     * @return 模型名称列表（如 ["qwen:latest", "llama3:8b"]）
     */
    @GetMapping("/models")
    public Mono<BaseResult<List<String>>> listModels() {
        log.info("📩 接收模型列表查询请求");
        return ollamaServiceManager.listModelNames()
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询模型列表失败: " + ex.getMessage())));
    }

    /**
     * 查询单个模型基础详情（名称、修改时间、大小）
     * @param modelName 模型名称
     * @return 模型基础信息
     */
    @GetMapping("/models/{modelName}")
    public Mono<BaseResult<OllamaModelsResponse.Model>> getModelDetail(
            @PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收模型详情查询请求 | 模型名称: {}", modelName);
        return ollamaServiceManager.getModelDetail(modelName)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询模型详情失败: " + ex.getMessage())));
    }

    /**
     * 查询当前运行中模型（内存中活跃模型）
     * @return 运行中模型列表
     */
    @GetMapping("/models/running")
    public Mono<BaseResult<OllamaModelsResponse>> listRunningModels() {
        log.info("📩 接收查询运行中模型请求");
        return ollamaServiceManager.listRunningModels()
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询运行中模型失败: " + ex.getMessage())));
    }

    /**
     * 查看模型完整配置（Modelfile + 量化类型 + 自定义参数）
     * @param modelName 模型名称
     * @return 模型完整配置信息
     */
    @GetMapping("/model/show/{modelName}")
    public Mono<BaseResult<Map<String, Object>>> showModel(
            @PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收查看模型详情请求 | 模型: {}", modelName);
        return ollamaServiceManager.showModel(modelName)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查看模型详情失败: " + ex.getMessage())));
    }

    // ------------------------------ 系统管理接口 ------------------------------
    /**
     * 服务健康检查（端点状态 + Ollama版本 + 服务地址）
     * @return 健康检查结果
     */
    @GetMapping("/health")
    public Mono<BaseResult<OllamaServiceManager.HealthCheckResult>> healthCheck() {
        log.info("📩 接收健康检查请求");
        return ollamaServiceManager.healthCheck()
                .map(BaseResult::success)
                .onErrorResume(ex -> Mono.just(BaseResult.error("健康检查失败: " + ex.getMessage())));
    }

    /**
     * 切换本地/远程Ollama服务端点
     * @param endpoint 目标端点（local/remote）
     * @return 切换结果
     */
    @PostMapping("/endpoint/switch")
    public BaseResult<Void> switchEndpoint(@RequestParam @NotBlank(message = "端点不能为空") String endpoint) {
        log.info("📩 接收端点切换请求 | 目标端点: {}", endpoint);
        try {
            ollamaServiceManager.setActiveEndpoint(endpoint);
            return BaseResult.success();
        } catch (IllegalArgumentException e) {
            return BaseResult.failure("INVALID_ENDPOINT", e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("切换端点失败: " + e.getMessage());
        }
    }

    /**
     * 查询Ollama服务版本
     * @return Ollama版本号（如 "0.1.30"）
     */
    @GetMapping("/version")
    public Mono<BaseResult<String>> getVersion() {
        log.info("📩 接收查询Ollama版本请求");
        // 修复：直接使用服务层返回的 Mono<String>，无嵌套包装
        return ollamaServiceManager.getActiveClient().getVersion()
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询版本失败: " + ex.getMessage())));
    }

    // ------------------------------ 高级功能接口 ------------------------------
    /**
     * 文本补全（流式返回，适用于长文本生成）
     * @param request 补全请求参数（模型名 + 提示词 + 可选参数）
     * @return 流式补全文本片段
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@Validated @RequestBody GenerateRequest request) {
        log.info("📩 接收文本补全请求 | 模型: {} | 提示词: {}", request.getModel(), maskMessage(request.getPrompt()));
        return ollamaServiceManager.generate(request)
                .map(content -> "data: " + content + "\n\n")
                .onErrorResume(BaseException.class, ex -> Flux.just("data: 错误: " + ex.getMessage() + "\n\n"))
                .onErrorResume(ex -> Flux.just("data: 文本补全失败: " + ex.getMessage() + "\n\n"));
    }

    /**
     * 生成文本嵌入向量（用于语义搜索、文本相似度计算）
     * @param request 嵌入请求参数（模型名 + 输入文本）
     * @return 文本对应的嵌入向量列表
     */
    @PostMapping("/embed")
    public Mono<BaseResult<List<List<Double>>>> embed(@Validated @RequestBody EmbedRequest request) {
        log.info("📩 接收生成嵌入请求 | 模型: {} | 输入: {}", request.getModel(), maskMessage(request.getInput().toString()));
        return ollamaServiceManager.embed(request)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("生成嵌入失败: " + ex.getMessage())));
    }

    /**
     * 拉取模型（从Ollama仓库下载，流式返回进度）
     * @param request 拉取请求参数（模型名 + 可选配置）
     * @return 拉取进度信息（百分比 + 状态）
     */
    @PostMapping(value = "/model/pull", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> pullModel(@Validated @RequestBody ModelManageRequest request) {
        log.info("📩 接收拉取模型请求 | 模型: {}", request.getModel());
        return ollamaServiceManager.pullModel(request)
                .map(progress -> "data: " + progress + "\n\n")
                .onErrorResume(BaseException.class, ex -> Flux.just("data: 错误: " + ex.getMessage() + "\n\n"))
                .onErrorResume(ex -> Flux.just("data: 拉取模型失败: " + ex.getMessage() + "\n\n"));
    }

    /**
     * 删除模型（从本地Ollama服务移除）
     * @param modelName 模型名称
     * @return 删除结果（true=成功，false=失败）
     */
    @DeleteMapping("/model/{modelName}")
    public Mono<BaseResult<Boolean>> deleteModel(
            @PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收删除模型请求 | 模型: {}", modelName);
        return ollamaServiceManager.deleteModel(modelName)
                // 显式指定泛型为 Boolean，确保类型一致
                .map(success -> success
                        ? BaseResult.success(true)
                        : BaseResult.<Boolean>failure("DELETE_FAILED", "删除模型失败（模型不存在或无权限）")
                )
                // 异常处理：显式指定泛型为 Boolean
                .onErrorResume(BaseException.class, ex -> Mono.just(
                        BaseResult.<Boolean>failure(ex.getErrorCode(), ex.getMessage())
                ))
                .onErrorResume(ex -> Mono.just(
                        BaseResult.<Boolean>error("删除模型失败: " + ex.getMessage())
                ));
    }

    /**
     * 复制模型（源模型 → 目标模型，快速创建自定义变体）
     * @param request 复制请求参数（源模型名 + 目标模型名）
     * @return 复制结果（true=成功，false=失败）
     */
    @PostMapping("/model/copy")
    public Mono<BaseResult<Boolean>> copyModel(@Validated @RequestBody ModelManageRequest request) {
        log.info("📩 接收复制模型请求 | 源模型: {} | 目标模型: {}", request.getSource(), request.getDestination());
        return ollamaServiceManager.copyModel(request)
                // 显式指定泛型为 Boolean，确保所有分支返回类型一致
                .map(success -> success
                        ? BaseResult.success(true)
                        : BaseResult.<Boolean>failure("COPY_FAILED", "复制模型失败（源模型不存在或目标模型已存在）")
                )
                // 异常处理：显式指定泛型为 Boolean
                .onErrorResume(BaseException.class, ex -> Mono.just(
                        BaseResult.<Boolean>failure(ex.getErrorCode(), ex.getMessage())
                ))
                .onErrorResume(ex -> Mono.just(
                        BaseResult.<Boolean>error("复制模型失败: " + ex.getMessage())
                ));
    }

    /**
     * 创建自定义模型（基于基础模型，支持自定义系统提示、模板、量化类型）
     * @param request 创建请求参数（新模型名 + 基础模型名 + 可选配置）
     * @return 创建进度信息
     */
    @PostMapping(value = "/model/create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> createModel(@Validated @RequestBody ModelManageRequest request) {
        log.info("📩 接收创建模型请求 | 新模型: {} | 基础模型: {}", request.getModel(), request.getFrom());
        return ollamaServiceManager.createModel(request)
                .map(progress -> "data: " + progress + "\n\n")
                .onErrorResume(BaseException.class, ex -> Flux.just("data: 错误: " + ex.getMessage() + "\n\n"))
                .onErrorResume(ex -> Flux.just("data: 创建模型失败: " + ex.getMessage() + "\n\n"));
    }

    // ------------------------------ 私有工具方法 ------------------------------
    /**
     * 日志消息脱敏（截取前20字符，避免敏感信息泄露）
     */
    private String maskMessage(String message) {
        if (message == null) return "null";
        return message.length() > 20 ? message.substring(0, 20) + "..." : message;
    }
}