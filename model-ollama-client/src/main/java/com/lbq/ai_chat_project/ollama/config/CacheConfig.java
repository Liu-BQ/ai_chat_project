package com.lbq.ai_chat_project.ollama.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * 缓存配置类：通过@Value直接读取配置文件中的缓存参数
 */
@Configuration
public class CacheConfig {

    // 读取缓存过期时间（默认5分钟），对应yml中的cache.caffeine.model-cache-expire
    // 注意：Duration类型要加#{T(java.time.Duration).parse('${xxx}')}，或直接用字符串转
    @Value("${cache.caffeine.model-cache-expire:PT5M}") // PT5M是Duration的默认格式（5分钟）
    private Duration modelCacheExpire;

    // 读取缓存最大容量（默认100），对应yml中的cache.caffeine.maximum-size
    @Value("${cache.caffeine.maximum-size:100}")
    private long maximumSize;

    /**
     * 配置缓存管理器：使用@Value注入的配置参数
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 用@Value获取的参数构建Caffeine规则
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(modelCacheExpire) // 过期时间（来自配置文件）
                .maximumSize(maximumSize); // 最大容量（来自配置文件）

        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}