package com.lbq.ai_chat_project.ollama.dto.chat;

import lombok.Data;

/**
 * Ollama 聊天响应 DTO。
 * <p>
 * 用于封装从 Ollama 服务返回的聊天响应。
 * </p>
 * <p>
 * ✅ 保留核心字段：模型、响应内容、完成状态
 * ✅ 移除了冗余字段：总耗时、加载耗时
 * </p>
 */
@Data
public class OllamaChatResponse {
    /** 使用的模型名称 */
    private String model;

    /** 模型生成的文本响应 */
    private String response;

    /** 是否已完全生成（非流式） */
    private boolean done;
}