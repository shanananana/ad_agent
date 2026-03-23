package com.example.adagent.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <strong>提示词资源加载器</strong>：从 {@code classpath:prompts/} 读取 {@code .txt} 纯文本或渲染
 * {@link org.springframework.ai.chat.prompt.PromptTemplate}；业务代码通过 {@link PromptResourcePaths} 传入相对路径，避免魔法字符串。
 */
@Component
public class ClasspathPromptLoader {

    private static final String PREFIX = "prompts/";

    private final ConcurrentHashMap<String, String> textCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * 原始 UTF-8 文本（不解析占位符）。
     */
    public String loadText(String relativePath) {
        return textCache.computeIfAbsent(relativePath, this::loadTextUncached);
    }

    /**
     * 可渲染的模板，占位符语法为 {@code {variable}}（与 Spring AI {@link PromptTemplate} 一致）。
     */
    public PromptTemplate loadTemplate(String relativePath) {
        return templateCache.computeIfAbsent(relativePath, path ->
                new PromptTemplate(new ClassPathResource(PREFIX + path)));
    }

    /**
     * 加载模板并渲染为单条字符串。
     */
    public String renderTemplate(String relativePath, Map<String, Object> model) {
        return loadTemplate(relativePath).render(model);
    }

    private String loadTextUncached(String relativePath) {
        ClassPathResource res = new ClassPathResource(PREFIX + relativePath);
        if (!res.exists()) {
            throw new IllegalStateException("Missing classpath resource: " + PREFIX + relativePath);
        }
        try {
            return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt: " + relativePath, e);
        }
    }
}
