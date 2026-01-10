package com.lbq.ai_chat_project.ollama.constant;

/**
 * Ollama 相关的静态常量定义（完整版）
 * 集中管理所有魔法值，避免硬编码，包含 API 路径、JSON 字段名、连接池配置等
 */
public final class OllamaConstants {

    /** 私有构造函数，防止实例化 */
    private OllamaConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** Ollama API 路径常量（包含所有核心接口路径） */
    public static class ApiPath {
        /** 聊天接口路径 */
        public static final String CHAT = "/api/chat";
        /** 模型标签接口路径 */
        public static final String TAGS = "/api/tags";
        /** 文本补全接口路径 */
        public static final String GENERATE = "/api/generate";
        /** 生成嵌入向量接口路径 */
        public static final String EMBED = "/api/embed";
        /** 拉取模型接口路径 */
        public static final String PULL = "/api/pull";
        /** 删除模型接口路径 */
        public static final String DELETE = "/api/delete";
        /** 复制模型接口路径 */
        public static final String COPY = "/api/copy";
        /** 创建自定义模型接口路径 */
        public static final String CREATE = "/api/create";
        /** 查看模型详情接口路径 */
        public static final String SHOW = "/api/show"; // 补充路径常量
        /** 列出运行中模型接口路径 */
        public static final String PS = "/api/ps";
        /** 查询Ollama服务版本接口路径 */
        public static final String VERSION = "/version";
    }

    /** JSON 响应/请求字段名常量（统一管理接口交互的字段） */
    public static class JsonField {
        /** 文本补全响应中的「生成内容」字段 */
        public static final String RESPONSE = "response";
        /** 流式响应中的「生成完成」状态字段 */
        public static final String DONE = "done";
        /** 嵌入向量响应中的「向量列表」字段 */
        public static final String EMBEDDINGS = "embeddings";
    }

    /** 连接池相关常量 */
    public static class Pool {
        /** Netty HTTP 连接池名称 */
        public static final String CONNECTION_POOL_NAME = "ollama-http-pool";
        /** 连接池获取连接超时时间（毫秒） */
        public static final long PENDING_ACQUIRE_TIMEOUT_MS = 3000L;
        /** 连接池最大空闲时间（分钟） */
        public static final long MAX_IDLE_TIME_MINUTES = 5L;
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