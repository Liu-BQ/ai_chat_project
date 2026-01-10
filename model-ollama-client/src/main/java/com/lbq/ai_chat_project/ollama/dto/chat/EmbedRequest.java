package com.lbq.ai_chat_project.ollama.dto.chat;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Ollama 生成嵌入请求 DTO（对应 /api/embed 接口）
 */
@Data
public class EmbedRequest {
    /** 模型名称（必填，如 all-minilm） */
    private String model;
    /** 输入文本（必填，单个文本或文本列表） */
    private Object input; // 支持 String 或 List<String>
    /** 是否截断超长文本（默认 true，可选） */
    private boolean truncate = true;
    /** 嵌入向量维度（可选） */
    private Integer dimensions;
    /** 模型参数（可选） */
    private Map<String, Object> options;
    /** 模型内存保留时长（默认 5m，可选） */
    private String keep_alive = "5m";
}