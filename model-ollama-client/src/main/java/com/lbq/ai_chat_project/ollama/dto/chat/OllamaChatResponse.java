package com.lbq.ai_chat_project.ollama.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * Ollama 聊天响应 DTO。
 * <p>
 * 映射 Ollama /api/chat 接口的真实 JSON 结构：
 * {
 *   "model": "qwen:7b",
 *   "message": { "role": "assistant", "content": "你好！" },
 *   "done": true,
 *   ...
 * }
 * </p>
 */
@Data
public class OllamaChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型名称 */
    private String model;

    /** 消息内容（核心字段） */
    private Message message;

    /** 是否完成生成 */
    private Boolean done;

    // 其他可选性能指标（按需保留）
    private Long totalDuration;
    private Long loadDuration;
    private Integer promptEvalCount;
    private Integer evalCount;
    private Long evalDuration;

    @Data
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        private String role;
        private String content; // ← 关键：AI 回复文本在此
    }

    /**
     * 安全获取 AI 生成的回复文本。
     *
     * @return 回复内容，若缺失则返回 null
     */
    public String getReplyContent() {
        return (message != null) ? message.getContent() : null;
    }

    /**
     * 安全判断是否生成完成。
     *
     * @return true 表示完成，false 表示未完成或未知
     */
    public boolean isDone() {
        return Boolean.TRUE.equals(done);
    }
}