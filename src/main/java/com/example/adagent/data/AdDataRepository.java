package com.example.adagent.data;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.data.dto.CampaignBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 基础数据读写：按用户隔离，单文件 data/base/users/{userId}/campaigns.json。
 * 只读模板 data/base/_template_campaigns.json 禁止修改；当前用户无文件时从模板复制一份（带 uid），再读。
 */
@Repository
public class AdDataRepository {

    private static final Logger logger = LoggerFactory.getLogger(AdDataRepository.class);
    private static final Pattern ID_CAMPAIGN = Pattern.compile("c(\\d+)");
    private static final Pattern ID_AD_GROUP = Pattern.compile("ag(\\d+)");
    private static final Pattern ID_AD = Pattern.compile("a(\\d+)");
    private static final Pattern ID_CREATIVE = Pattern.compile("cr(\\d+)");

    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;

    public AdDataRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void ensureDataDir() throws IOException {
        Files.createDirectories(dataPathConfig.getBaseDataPath().getParent());
        Files.createDirectories(dataPathConfig.getPerformanceDataPath().getParent());
        java.nio.file.Path templatePath = dataPathConfig.getBaseDataTemplatePath();
        java.nio.file.Path globalPath = dataPathConfig.getBaseDataPath();
        if (!Files.exists(templatePath) && Files.exists(globalPath)) {
            Files.copy(globalPath, templatePath);
            logger.info("【数据层】已从现有 campaigns.json 生成只读模板 _template_campaigns.json，后续禁止修改模板");
        }
        logger.info("【数据层】本地数据目录已就绪");
    }

    /**
     * 加载当前用户基础数据。若该用户尚无文件且传了 userId，则先从只读模板复制一份到用户目录（禁止修改模板），再加载。
     * @param userId 为 null/空时使用全局路径（兼容旧逻辑）
     */
    public CampaignBase loadBase(String userId) {
        java.nio.file.Path path = dataPathConfig.getBaseDataPath(userId);
        if (!Files.exists(path)) {
            if (userId != null && !userId.isBlank()) {
                ensureUserBaseFromTemplate(userId);
                path = dataPathConfig.getBaseDataPath(userId);
                if (!Files.exists(path)) {
                    return emptyBase();
                }
            } else {
                return emptyBase();
            }
        }
        try {
            String json = Files.readString(path);
            CampaignBase base = objectMapper.readValue(json, CampaignBase.class);
            if (base.getCampaigns() == null) {
                base.setCampaigns(new ArrayList<>());
            }
            return base;
        } catch (Exception e) {
            logger.error("【数据层】读取基础数据失败: {}", path, e);
            return emptyBase();
        }
    }

    /** 当前用户无基础数据时：从只读模板复制到用户目录；无模板则写空数据。禁止修改模板文件。 */
    private void ensureUserBaseFromTemplate(String userId) {
        java.nio.file.Path userPath = dataPathConfig.getBaseDataPath(userId);
        java.nio.file.Path templatePath = dataPathConfig.getBaseDataTemplatePath();
        try {
            Files.createDirectories(userPath.getParent());
            if (Files.exists(templatePath)) {
                Files.copy(templatePath, userPath);
                logger.info("【数据层】已从只读模板为 userId={} 初始化基础数据", userId);
            } else {
                CampaignBase empty = emptyBase();
                saveBase(userId, empty);
                logger.info("【数据层】无模板，已为 userId={} 写入空基础数据", userId);
            }
        } catch (IOException e) {
            logger.error("【数据层】初始化用户基础数据失败 userId={}", userId, e);
            throw new RuntimeException("初始化用户基础数据失败", e);
        }
    }

    private static CampaignBase emptyBase() {
        CampaignBase empty = new CampaignBase();
        empty.setUpdatedAt(Instant.now().toString());
        empty.setCampaigns(new ArrayList<>());
        return empty;
    }

    /** 写入基础数据到对应用户文件；绝不写入模板路径。 */
    public void saveBase(String userId, CampaignBase base) {
        base.setUpdatedAt(Instant.now().toString());
        java.nio.file.Path path = dataPathConfig.getBaseDataPath(userId);
        if (path.equals(dataPathConfig.getBaseDataTemplatePath())) {
            throw new IllegalStateException("禁止修改只读模板基础数据");
        }
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), base);
            logger.info("【数据层】已写入基础数据: {}", path);
        } catch (Exception e) {
            logger.error("【数据层】写入基础数据失败: {}", path, e);
            throw new RuntimeException("写入基础数据失败", e);
        }
    }

    /** 自增 ID：下一个计划 ID，如 c1, c2（基于对应用户数据） */
    public String nextCampaignId(String userId) {
        CampaignBase base = loadBase(userId);
        int max = 0;
        for (CampaignBase.Campaign c : base.getCampaigns()) {
            if (c.getId() != null) {
                var m = ID_CAMPAIGN.matcher(c.getId());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return "c" + (max + 1);
    }

    public String nextAdGroupId(List<CampaignBase.AdGroup> adGroups) {
        int max = 0;
        for (CampaignBase.AdGroup ag : adGroups) {
            if (ag.getId() != null) {
                var m = ID_AD_GROUP.matcher(ag.getId());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return "ag" + (max + 1);
    }

    public String nextAdId(List<CampaignBase.Ad> ads) {
        int max = 0;
        for (CampaignBase.Ad a : ads) {
            if (a.getId() != null) {
                var m = ID_AD.matcher(a.getId());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return "a" + (max + 1);
    }

    public String nextCreativeId(List<CampaignBase.Creative> creatives) {
        int max = 0;
        for (CampaignBase.Creative cr : creatives) {
            if (cr.getId() != null) {
                var m = ID_CREATIVE.matcher(cr.getId());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return "cr" + (max + 1);
    }

    public Optional<CampaignBase.Campaign> findCampaign(String campaignId, String userId) {
        return loadBase(userId).getCampaigns().stream()
                .filter(c -> campaignId.equals(c.getId()))
                .findFirst();
    }
}
