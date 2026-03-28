package com.shanananana.adagent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MarkdownSkillLoader {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSkillLoader.class);

    public List<ParsedSkill> loadAll(
            MarkdownSkillsProperties props,
            MarkdownSkillParser parser,
            ResourceLoader resourceLoader) {
        List<ParsedSkill> out = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        var resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        for (String loc : props.getLocations()) {
            if (loc == null || loc.isBlank()) {
                continue;
            }
            try {
                Resource[] resources = resolver.getResources(loc);
                for (Resource resource : resources) {
                    if (!resource.isReadable()) {
                        continue;
                    }
                    String desc = resource.getDescription();
                    try (InputStream in = resource.getInputStream()) {
                        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        ParsedSkill ps = parser.parse(text, desc);
                        if (ps == null) {
                            continue;
                        }
                        if (!seenIds.add(ps.id())) {
                            log.error("重复 skill id「{}」，跳过：{}", ps.id(), desc);
                            continue;
                        }
                        out.add(ps);
                    } catch (IOException e) {
                        log.error("读取 skill 失败 {}：{}", desc, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.error("解析路径模式失败「{}」：{}", loc, e.getMessage());
            }
        }
        return out;
    }
}
