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

import java.util.List;
import java.util.Map;

/**
 * Ollama前端API控制器（完整版，支持所有核心接口）
 */
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OllamaController {
    private final OllamaServiceManager ollamaServiceManager;

    // ------------------------------ 已有接口（保持不变） ------------------------------
    @PostMapping("/chat")
    public Mono<BaseResult<String>> chat(
            @RequestParam @NotBlank(message = "模型名称不能为空") String model,
            @RequestParam @NotBlank(message = "用户消息不能为空") String message) {
        log.info("📩 接收非流式聊天请求 | 模型: {} | 用户消息: {}", model, maskMessage(message));
        return ollamaServiceManager.chat(model, message)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("聊天失败: " + ex.getMessage())));
    }

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

    @GetMapping("/models")
    public Mono<BaseResult<List<String>>> listModels() {
        log.info("📩 接收模型列表查询请求");
        return ollamaServiceManager.listModelNames()
                .map(BaseResult::success)
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询模型列表失败: " + ex.getMessage())));
    }

    @GetMapping("/models/{modelName}")
    public Mono<BaseResult<OllamaModelsResponse.Model>> getModelDetail(
            @PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收模型详情查询请求 | 模型名称: {}", modelName);
        return ollamaServiceManager.getModelDetail(modelName)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询模型详情失败: " + ex.getMessage())));
    }

    @GetMapping("/health")
    public Mono<BaseResult<OllamaServiceManager.HealthCheckResult>> healthCheck() {
        log.info("📩 接收健康检查请求");
        return ollamaServiceManager.healthCheck()
                .map(BaseResult::success)
                .onErrorResume(ex -> Mono.just(BaseResult.error("健康检查失败: " + ex.getMessage())));
    }

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

    // ------------------------------ 新增对外接口 ------------------------------
    /**
     * 文本补全接口（对应 /api/generate）
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
     * 生成嵌入向量接口（对应 /api/embed）
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
     * 拉取模型接口（对应 /api/pull）
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
     * 删除模型接口（对应 /api/delete）
     */
    @DeleteMapping("/model/{modelName}")
    public Mono<BaseResult<Boolean>> deleteModel(@PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收删除模型请求 | 模型: {}", modelName);
        return ollamaServiceManager.deleteModel(modelName)
                .map(success -> success ? BaseResult.success(true) : BaseResult.failure("DELETE_FAILED", "删除模型失败"))
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("删除模型失败: " + ex.getMessage())));
    }

    /**
     * 复制模型接口（对应 /api/copy）
     */
    @PostMapping("/model/copy")
    public Mono<BaseResult<Boolean>> copyModel(@Validated @RequestBody ModelManageRequest request) {
        log.info("📩 接收复制模型请求 | 源模型: {} | 目标模型: {}", request.getSource(), request.getDestination());
        return ollamaServiceManager.copyModel(request)
                .map(success -> success ? BaseResult.success(true) : BaseResult.failure("COPY_FAILED", "复制模型失败"))
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("复制模型失败: " + ex.getMessage())));
    }

    /**
     * 创建自定义模型接口（对应 /api/create）
     */
    @PostMapping(value = "/model/create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> createModel(@Validated @RequestBody ModelManageRequest request) {
        log.info("📩 接收创建模型请求 | 新模型: {} | 基础模型: {}", request.getModel(), request.getFrom());
        return ollamaServiceManager.createModel(request)
                .map(progress -> "data: " + progress + "\n\n")
                .onErrorResume(BaseException.class, ex -> Flux.just("data: 错误: " + ex.getMessage() + "\n\n"))
                .onErrorResume(ex -> Flux.just("data: 创建模型失败: " + ex.getMessage() + "\n\n"));
    }

    /**
     * 查看模型详情接口（对应 /api/show）
     */
    @GetMapping("/model/show/{modelName}")
    public Mono<BaseResult<Map<String, Object>>> showModel(@PathVariable @NotBlank(message = "模型名称不能为空") String modelName) {
        log.info("📩 接收查看模型详情请求 | 模型: {}", modelName);
        return ollamaServiceManager.showModel(modelName)
                .map(BaseResult::success)
                .onErrorResume(BaseException.class, ex -> Mono.just(BaseResult.failure(ex.getErrorCode(), ex.getMessage())))
                .onErrorResume(ex -> Mono.just(BaseResult.error("查看模型详情失败: " + ex.getMessage())));
    }

    /**
     * 列出运行中模型接口（对应 /api/ps）
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
     * 查询Ollama服务版本接口
     */
    @GetMapping("/version")
    public Mono<BaseResult<String>> getVersion() {
        log.info("📩 接收查询Ollama版本请求");
        return Mono.just(ollamaServiceManager.getActiveClient().getVersion())
                .map(BaseResult::success)
                .onErrorResume(ex -> Mono.just(BaseResult.error("查询版本失败: " + ex.getMessage())));
    }

    // ------------------------------ 私有工具方法 ------------------------------
    private String maskMessage(String message) {
        if (message == null) return "null";
        return message.length() > 20 ? message.substring(0, 20) + "..." : message;
    }
}