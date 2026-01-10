package com.lbq.ai_chat_project.ollama.dto.model;

import lombok.Data;

import java.util.Map;

/**
 * 模型管理请求 DTO（拉取、复制、创建模型用）
 */
@Data
public class ModelManageRequest {
    /** 模型名称（必填） */
    private String model;
    /** 源模型名称（复制、创建模型用，可选） */
    private String source;
    /** 目标模型名称（复制模型用，必填） */
    private String destination;
    /** 基础模型名称（创建模型用，可选） */
    private String from;
    /** 系统提示（创建模型用，可选） */
    private String system;
    /** 提示模板（创建模型用，可选） */
    private String template;
    /** 量化类型（创建模型用，可选，如 q4_K_M、q8_0） */
    private String quantize;
    /** 模型参数（创建模型用，可选） */
    private Map<String, Object> parameters;
    /** 是否流式返回进度（拉取、创建模型用，默认 true） */
    private boolean stream = true;
    /** 允许不安全连接（拉取模型用，可选） */
    private boolean insecure = false;
}