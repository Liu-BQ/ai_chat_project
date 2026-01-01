package com.lbq.ai_chat_project.ollama.dto.chat;

import lombok.Data;
import java.util.List;

/**
 * Ollama 聊天请求 DTO。
 * <p>
 * 用于封装发送给 Ollama 服务的聊天请求参数。
 * </p>
 * <p>
 * ✅ 简化结构：移除了冗余字段
 * ✅ 保留核心功能：模型、消息、流式传输
 * </p>
 */
@Data
public class OllamaChatRequest {
    /** 默认模型：qwen:latest */
    private String model = "qwen:latest";

    /** 用户输入的提示文本 */
    private String prompt;

    /** 消息历史（对话记录） */
    private List<Message> messages;

    /** 是否启用流式响应（默认关闭） */
    private boolean stream = false;

    @Data
    public static class Message {
        /** 消息角色：user/assistant */
        private String role;

        /** 消息内容 */
        private String content;
    }
}