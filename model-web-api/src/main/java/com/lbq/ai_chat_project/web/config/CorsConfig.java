package com.lbq.ai_chat_project.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 全局CORS配置（配置项从配置文件读取）
 */
@Configuration
public class CorsConfig {

    // ========== 从配置文件读取跨域配置项（带默认值，避免配置缺失报错） ==========
    /**
     * 允许的跨域源（多个用逗号分隔，默认前端本地开发地址）
     * 配置文件key：cors.allowed-origins
     */
    @Value("${cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 允许的请求方法（默认所有）
     * 配置文件key：cors.allowed-methods
     */
    @Value("${cors.allowed-methods:*}")
    private String allowedMethods;

    /**
     * 允许的请求头（默认所有）
     * 配置文件key：cors.allowed-headers
     */
    @Value("${cors.allowed-headers:*}")
    private String allowedHeaders;

    /**
     * 是否允许携带Cookie（默认true）
     * 配置文件key：cors.allow-credentials
     */
    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * 预检请求缓存时间（秒，默认3600）
     * 配置文件key：cors.max-age
     */
    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. 处理允许的源（逗号分隔转List）
        List<String> originList = Arrays.asList(allowedOrigins.split(","));
        originList.forEach(config::addAllowedOrigin);

        // 2. 处理允许的请求方法
        config.addAllowedMethod(allowedMethods);

        // 3. 处理允许的请求头
        config.addAllowedHeader(allowedHeaders);

        // 4. 是否允许携带Cookie
        config.setAllowCredentials(allowCredentials);

        // 5. 预检请求缓存时间
        config.setMaxAge(maxAge);

        // 配置所有接口都允许跨域
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}