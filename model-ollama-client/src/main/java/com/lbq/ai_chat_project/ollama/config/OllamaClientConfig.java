package com.lbq.ai_chat_project.ollama.config;

import com.lbq.ai_chat_project.ollama.client.OllamaClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

/**
 * Ollama 客户端的 Spring 配置类（适配ObjectMapper注入）
 * 负责创建本地/远程OllamaClient Bean，注入Spring自动配置的ObjectMapper
 */
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaClientConfig {

    /**
     * 创建本地 Ollama 客户端（主 Bean）
     * 注入Spring自动配置的ObjectMapper，解决JSON解析报错问题
     */
    @Bean(name = "localOllamaClient")
    @Primary
    public OllamaClient localOllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
        return new OllamaClient(properties.getLocal(), objectMapper);
    }

    /**
     * 创建远程 Ollama 客户端
     */
    @Bean(name = "remoteOllamaClient")
    public OllamaClient remoteOllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
        return new OllamaClient(properties.getRemote(), objectMapper);
    }
}