package com.lbq.ai_chat_project.ollama.config;

import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Ollama 客户端的 Spring 配置类（无容错机制）。
 * <p>
 * 负责创建两个独立的 OllamaClient Bean：
 * - localOllamaClient：用于连接本地开发环境的 Ollama 服务
 * - remoteOllamaClient：用于连接远程生产环境的 Ollama 服务
 * </p>
 * <p>
 * ✅ 简化配置：移除了 Resilience4j 依赖和相关配置
 * ✅ 保留核心功能：连接池、超时等参数仍可动态配置
 * </p>
 */
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaClientConfig {

    /**
     * 创建本地 Ollama 客户端（主 Bean）
     *
     * @param properties Ollama 全局配置
     * @return 配置好的 OllamaClient 实例
     */
    @Bean(name = "localOllamaClient")
    @Primary
    public OllamaClient localOllamaClient(OllamaProperties properties) {
        return new OllamaClient(properties.getLocal());
    }

    /**
     * 创建远程 Ollama 客户端
     *
     * @param properties Ollama 全局配置
     * @return 配置好的 OllamaClient 实例
     */
    @Bean(name = "remoteOllamaClient")
    public OllamaClient remoteOllamaClient(OllamaProperties properties) {
        return new OllamaClient(properties.getRemote());
    }
}