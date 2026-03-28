package com.shanananana.adagent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownSkillParser {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSkillParser.class);
    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*\\R(.*?)\\R---\\s*\\R(.*)\\z", Pattern.DOTALL);

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public MarkdownSkillParser(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public ParsedSkill parse(String fullText, String sourceDescription) {
        if (fullText == null) {
            return null;
        }
        Matcher m = FRONT_MATTER.matcher(fullText);
        if (!m.matches()) {
            log.error("Markdown skill 须以 YAML front matter（--- ... ---）开头，跳过：{}", sourceDescription);
            return null;
        }
        String yamlPart = m.group(1).trim();
        String body = m.group(2);
        Map<String, Object> map;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yamlMapper.readValue(yamlPart, LinkedHashMap.class);
            map = raw;
        } catch (Exception e) {
            log.error("YAML 解析失败 {}：{}", sourceDescription, e.getMessage());
            return null;
        }
        String id = stringVal(map.get("id"));
        String name = stringVal(map.get("name"));
        String description = stringVal(map.get("description"));
        if (id.isBlank() || name.isBlank() || description.isBlank()) {
            log.error("front matter 须含非空 id、name、description，跳过：{}", sourceDescription);
            return null;
        }
        String inputSchemaJson = null;
        Object schemaObj = map.get("input_schema");
        if (schemaObj != null) {
            try {
                inputSchemaJson = jsonMapper.writeValueAsString(schemaObj);
            } catch (Exception e) {
                log.error("input_schema 序列化失败 {}：{}", sourceDescription, e.getMessage());
                return null;
            }
        }
        return new ParsedSkill(id, name, description, inputSchemaJson, body == null ? "" : body, sourceDescription);
    }

    private static String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
