package com.lbq.ai_chat_project.ollama.dto.chat;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Ollama 文本补全请求 DTO（对应 /api/generate 接口）
 */
@Data
public class GenerateRequest {
    /** 模型名称（必填） */
    private String model;
    /** 提示词（必填） */
    private String prompt;
    /** 文本后缀（可选） */
    private String suffix;
    /** Base64编码图片列表（多模态模型用，可选） */
    private List<String> images;
    /** 是否流式响应（默认 true） */
    private boolean stream = true;
    /** 输出格式（json 或 JSON Schema，可选） */
    private Object format;
    /** 模型参数（如 temperature、top_k 等，可选） */
    private Map<String, Object> options;
    /** 系统提示（覆盖 Modelfile，可选） */
    private String system;
    /** 提示模板（覆盖 Modelfile，可选） */
    private String template;
    /** 是否禁用格式化（可选） */
    private boolean raw = false;
    /** 模型内存保留时长（默认 5m，可选） */
    private String keep_alive = "5m";
}