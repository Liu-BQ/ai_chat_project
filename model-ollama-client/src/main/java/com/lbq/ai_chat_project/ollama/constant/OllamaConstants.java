package com.lbq.ai_chat_project.ollama.constant;

/**
 * Ollama 相关的静态常量定义（简化版）。
 * <p>
 * 将魔法值（Magic Numbers/Strings）集中管理，避免硬编码。
 * 所有字段均为 {@code public static final}，编译期常量。
 * </p>
 * <p>
 * ✅ 移除了冗余常量：删除了 ErrorMessage 相关字段
 * ✅ 保留核心配置：API 路径、默认连接参数
 * </p>
 */
public final class OllamaConstants {

    /** 私有构造函数，防止实例化 */
    private OllamaConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** Ollama API 路径常量 */
    public static class ApiPath {
        /** 聊天接口路径 */
        public static final String CHAT = "/api/chat";
        /** 模型标签接口路径 */
        public static final String TAGS = "/api/tags";
    }

    /** 默认连接配置常量 */
    public static class Defaults {
        /** 默认本地 Ollama 服务地址 */
        public static final String DEFAULT_BASE_URL = "http://localhost:11434";

        /** 默认连接超时（毫秒） */
        public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000; // 5秒

        /** 默认读取超时（毫秒） */
        public static final int DEFAULT_READ_TIMEOUT_MS = 60_000; // 60秒

        /** 默认写入超时（毫秒） */
        public static final int DEFAULT_WRITE_TIMEOUT_MS = 30_000; // 30秒

        /** 默认连接池大小 */
        public static final int DEFAULT_CONNECTION_POOL_SIZE = 50;
    }
}