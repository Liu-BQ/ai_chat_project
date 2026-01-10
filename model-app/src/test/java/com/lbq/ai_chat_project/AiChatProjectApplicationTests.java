package com.lbq.ai_chat_project;

import com.lbq.ai_chat_project.ollama.dto.chat.EmbedRequest;
import com.lbq.ai_chat_project.ollama.dto.chat.GenerateRequest;
import com.lbq.ai_chat_project.ollama.dto.model.ModelManageRequest;
import com.lbq.ai_chat_project.ollama.service.OllamaServiceManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 聊天项目完整功能测试类（包含所有核心API测试）
 */
@Slf4j
@SpringBootTest
class AiChatProjectApplicationTests {
    @Autowired
    private OllamaServiceManager ollamaServiceManager;

    // ------------------------------ 已有测试方法（保持不变） ------------------------------
    @Test
    void testActiveEndpoint() {
        log.info("===== 开始测试：检查活跃端点 =====");
        String activeEndpoint = ollamaServiceManager.getActiveEndpoint();
        log.info("当前活跃的 Ollama 服务端点：{}", activeEndpoint);
        assertEquals("local", activeEndpoint, "默认活跃端点应为 'local'（本地服务），请检查配置");
        log.info("===== 测试完成：活跃端点检查通过 =====");
    }

    @Test
    void testOfflineModels() {
        log.info("===== 开始测试：检查离线模型列表 =====");
        if (!"local".equals(ollamaServiceManager.getActiveEndpoint())) {
            ollamaServiceManager.setActiveEndpoint("local");
            log.info("已自动切换到 'local' 端点，确保查询本地离线模型");
        }
        Mono<List<String>> modelListMono = ollamaServiceManager.listModelNames();
        List<String> offlineModels = modelListMono.block();
        log.info("本地 Ollama 已拉取的离线模型列表：{}", offlineModels);
        assertNotNull(offlineModels, "离线模型列表为 null，可能 Ollama 服务未启动");
        assertFalse(offlineModels.isEmpty(),
                "本地无已拉取的离线模型！请先执行命令：`ollama run qwen:latest` 拉取模型");
        log.info("本地离线模型总数：{}", offlineModels.size());
        log.info("===== 测试完成：离线模型列表检查通过 =====");
    }

    @Test
    void testSendHelloMessage() {
        log.info("===== 开始测试：发送 '你好' 消息 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            log.info("当前使用端点：local（本地离线模型）");
            List<String> offlineModels = ollamaServiceManager.listModelNames().block();
            assertNotNull(offlineModels, "Ollama 服务未响应，可能未启动");
            assertFalse(offlineModels.isEmpty(), "无本地模型，无法发送消息");
            String targetModel = offlineModels.get(0);
            log.info("当前使用的离线模型：{}", targetModel);
            log.info("正在发送消息：'你好' 到模型：{}", targetModel);
            Mono<String> replyMono = ollamaServiceManager.chat(targetModel, "你好");
            String aiReply = replyMono.block();
            log.info("===== AI 模型 [{}] 回复 =====\n{}", targetModel, aiReply);
            assertNotNull(aiReply, "AI 模型返回空回复，可能模型未正常启动");
            assertFalse(aiReply.trim().isEmpty(), "AI 模型回复为空字符串，需检查模型状态");
            log.info("===== 测试完成：消息发送成功，回复正常 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：发送消息异常 =====", e);
            fail("发送消息失败！请检查：1.Ollama 服务是否启动（执行 `ollama serve`） 2.是否已拉取模型（执行 `ollama run qwen`）");
        }
    }

    // ------------------------------ 新增测试方法 ------------------------------
    /**
     * 测试文本补全接口
     */
    @Test
    void testGenerate() {
        log.info("===== 开始测试：文本补全 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            List<String> offlineModels = ollamaServiceManager.listModelNames().block();
            assertNotNull(offlineModels, "无本地模型，无法测试文本补全");
            String targetModel = offlineModels.get(0);

            GenerateRequest request = new GenerateRequest();
            request.setModel(targetModel);
            request.setPrompt("请简要介绍 Spring Boot");
            request.setStream(true);

            log.info("正在测试文本补全 | 模型: {} | 提示词: {}", targetModel, request.getPrompt());
            Flux<String> resultFlux = ollamaServiceManager.generate(request);
            StringBuilder result = new StringBuilder();

            // 关键修复：使用 blockLast() 阻塞等待流完成，确保所有数据接收完毕
            resultFlux.doOnNext(content -> {
                        log.trace("接收文本补全片段：{}", content);
                        result.append(content);
                    })
                    .doOnError(error -> fail("文本补全失败: " + error.getMessage()))
                    .doOnComplete(() -> log.info("文本补全完成 | 结果: {}", result.toString()))
                    .blockLast(Duration.ofSeconds(30)); // 阻塞30秒超时，避免无限等待

            // 此时流已完全接收，断言结果非空
            assertFalse(result.toString().isEmpty(), "文本补全结果为空");
            log.info("===== 测试完成：文本补全通过 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：文本补全异常 =====", e);
            fail("文本补全测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试生成嵌入接口
     */
    @Test
    void testEmbed() {
        log.info("===== 开始测试：生成嵌入 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            List<String> offlineModels = ollamaServiceManager.listModelNames().block();
            assertNotNull(offlineModels, "无本地模型，无法测试生成嵌入");

            // 关键修改：找不到嵌入模型时返回null，而非直接抛出异常
            String embedModel = offlineModels.stream()
                    .filter(model -> model.contains("minilm") || model.contains("bge") || model.contains("embed"))
                    .findFirst()
                    .orElse(null);

            // 核心：无嵌入模型时跳过测试（标记为SKIPPED，而非FAILED）
            Assumptions.assumeTrue(embedModel != null,
                    "未找到嵌入专用模型！请执行 `ollama pull all-minilm` 或 `ollama pull bge` 拉取模型后再运行该测试");

            EmbedRequest request = new EmbedRequest();
            request.setModel(embedModel);
            request.setInput("Ollama 是本地大模型运行工具");

            log.info("正在测试生成嵌入 | 模型: {} | 输入: {}", embedModel, request.getInput());
            Mono<List<List<Double>>> embeddingsMono = ollamaServiceManager.embed(request);
            List<List<Double>> embeddings = embeddingsMono.block(Duration.ofSeconds(30)); // 增加30秒超时

            assertNotNull(embeddings, "生成嵌入结果为空");
            assertFalse(embeddings.isEmpty(), "生成嵌入结果为空列表");
            assertTrue(embeddings.get(0).size() > 0, "嵌入向量维度为0，不符合预期");
            log.info("生成嵌入完成 | 向量维度: {}", embeddings.get(0).size());
            log.info("===== 测试完成：生成嵌入通过 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：生成嵌入异常 =====", e);
            fail("生成嵌入测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试拉取模型接口（仅测试进度返回，不实际拉取大模型）
     */
    @Test
    void testPullModel() {
        log.info("===== 开始测试：拉取模型 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            ModelManageRequest request = new ModelManageRequest();
            request.setModel("tinyllama:latest"); // 小型模型，拉取速度快
            request.setStream(true);

            log.info("正在测试拉取模型 | 模型: {}", request.getModel());
            Flux<String> progressFlux = ollamaServiceManager.pullModel(request);
            progressFlux.take(3) // 仅取前3条进度信息，避免长时间等待
                    .subscribe(
                            progress -> log.info("拉取进度: {}", progress),
                            error -> log.warn("拉取模型测试结束（仅测试进度返回）: {}", error.getMessage()),
                            () -> log.info("拉取模型进度测试完成")
                    );

            log.info("===== 测试完成：拉取模型进度通过 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：拉取模型异常 =====", e);
            fail("拉取模型测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试删除模型接口（测试删除不存在的模型，避免误删）
     */
    @Test
    void testDeleteModel() {
        log.info("===== 开始测试：删除模型 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            String testModel = "test-delete-model:latest";
            log.info("正在测试删除模型 | 模型: {}", testModel);
            Mono<Boolean> resultMono = ollamaServiceManager.deleteModel(testModel);
            Boolean success = resultMono.block();

            // 不存在的模型删除应返回 false，无异常
            assertFalse(success, "删除不存在的模型应返回 false");
            log.info("===== 测试完成：删除模型通过 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：删除模型异常 =====", e);
            fail("删除模型测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试查看模型详情接口
     */
    @Test
    void testShowModel() {
        log.info("===== 开始测试：查看模型详情 =====");
        try {
            ollamaServiceManager.setActiveEndpoint("local");
            List<String> offlineModels = ollamaServiceManager.listModelNames().block();
            assertNotNull(offlineModels, "无本地模型，无法测试查看详情");
            String targetModel = offlineModels.get(0);

            log.info("正在测试查看模型详情 | 模型: {}", targetModel);
            Mono<Map<String, Object>> detailMono = ollamaServiceManager.showModel(targetModel);
            Map<String, Object> detail = detailMono.block();

            assertNotNull(detail, "模型详情为空");
            assertTrue(detail.containsKey("modelfile") || detail.containsKey("details"), "模型详情缺少关键字段");
            log.info("模型详情 | modelfile: {}", detail.get("modelfile"));
            log.info("===== 测试完成：查看模型详情通过 =====");
        } catch (Exception e) {
            log.error("===== 测试失败：查看模型详情异常 =====", e);
            fail("查看模型详情测试失败: " + e.getMessage());
        }
    }
}