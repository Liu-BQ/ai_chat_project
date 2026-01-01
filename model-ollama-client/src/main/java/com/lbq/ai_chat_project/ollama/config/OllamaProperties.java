package com.lbq.ai_chat_project.ollama.config;

import com.lbq.ai_chat_project.ollama.constant.OllamaConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama 客户端的配置属性类（简化版）。
 * <p>
 * 通过 application.yml 中的 {@code ollama.local} 和 {@code ollama.remote} 配置块进行绑定。
 * 所有字段均有合理的默认值，来源于 {@link OllamaConstants.Defaults}。
 * </p>
 * <p>
 * ✅ 移除了冗余字段：删除了 enableRetry 和 apiKey
 * ✅ 保留核心配置：URL、超时、连接池
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private OllamaEndpoint local = new OllamaEndpoint();
    private OllamaEndpoint remote = new OllamaEndpoint();

    @Data
    public static class OllamaEndpoint {
        /** Ollama 服务的基础 URL（例如：http://localhost:11434） */
        private String baseUrl = OllamaConstants.Defaults.DEFAULT_BASE_URL;

        /** TCP 连接超时时间（毫秒） */
        private int connectTimeout = OllamaConstants.Defaults.DEFAULT_CONNECT_TIMEOUT_MS;

        /** 读取响应超时时间（毫秒） */
        private int readTimeout = OllamaConstants.Defaults.DEFAULT_READ_TIMEOUT_MS;

        /** 写入请求超时时间（毫秒） */
        private int writeTimeout = OllamaConstants.Defaults.DEFAULT_WRITE_TIMEOUT_MS;

        /** HTTP 连接池大小（Netty 底层复用 TCP 连接） */
        private int connectionPoolSize = OllamaConstants.Defaults.DEFAULT_CONNECTION_POOL_SIZE;
    }
}