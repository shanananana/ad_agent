package com.shanananana.adagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 将 {@code data/creative/assets/} 下的用户子目录映射为静态 Web 路径，使文生图落盘后的图片可通过 URL 被前端与素材目录引用。
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
