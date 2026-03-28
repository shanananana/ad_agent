package com.shanananana.adagent.agent.perception;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 广告域<strong>轻量实体抽取</strong>：当前以正则为主，从用户输入中解析 {@code campaignId}（如 c2）及数字列表等，
 * 供提示增强使用；细粒度参数仍主要依赖 LLM 在工具调用时从上下文填写。
 */
@Service
public class EntityExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(EntityExtractionService.class);
    private static final Pattern CAMPAIGN_ID = Pattern.compile("(?:计划)?[\\s]*[cC]?(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    public EntityResult extractEntities(String userInput) {
        try {
            Map<String, Object> entities = new HashMap<>();
            if (userInput != null && !userInput.isBlank()) {
                String campaignId = extractCampaignId(userInput);
                if (campaignId != null) {
                    entities.put("campaignId", campaignId);
                }
                var numbers = extractNumbers(userInput);
                if (!numbers.isEmpty()) {
                    entities.put("numbers", numbers);
                }
            }
            logger.debug("【感知理解层】实体抽取: {}", entities);
            return new EntityResult(entities);
        } catch (Exception e) {
            logger.error("【感知理解层】实体抽取失败", e);
            return new EntityResult(new HashMap<>());
        }
    }

    private String extractCampaignId(String input) {
        Matcher m = CAMPAIGN_ID.matcher(input);
        if (m.find()) {
            return "c" + m.group(1);
        }
        return null;
    }

    private java.util.List<String> extractNumbers(String input) {
        java.util.List<String> list = new java.util.ArrayList<>();
        Matcher m = NUMBER.matcher(input);
        while (m.find()) {
            list.add(m.group());
        }
        return list;
    }

    /**
     * 单次抽取结果：不可变地持有 {@code entities} 映射（如 {@code campaignId}、{@code numbers}），
     * 并提供 {@link #getCampaignId()} 等便捷读取。
     */
    public static class EntityResult {
        private final Map<String, Object> entities;

        public EntityResult(Map<String, Object> entities) {
            this.entities = entities != null ? entities : new HashMap<>();
        }

        public Map<String, Object> getEntities() {
            return entities;
        }

        public String getCampaignId() {
            return (String) entities.get("campaignId");
        }

        @Override
        public String toString() {
            return "EntityResult" + entities;
        }
    }
}
