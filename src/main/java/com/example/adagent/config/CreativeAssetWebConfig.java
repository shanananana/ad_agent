package com.example.adagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 将 {@code data/creative/assets/} 映射到 HTTP，供持久化文生图结果访问。
 */
@Configuration
public class CreativeAssetWebConfig implements WebMvcConfigurer {

    private final DataPathConfig dataPathConfig;

    public CreativeAssetWebConfig(DataPathConfig dataPathConfig) {
        this.dataPathConfig = dataPathConfig;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = dataPathConfig.getCreativeAssetsRoot().toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/api/ad-agent/creative/files/**")
                .addResourceLocations(location);
    }
}
