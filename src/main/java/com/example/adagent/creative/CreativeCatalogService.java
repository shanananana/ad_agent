package com.example.adagent.creative;

import com.example.adagent.data.AdDataRepository;
import com.example.adagent.data.ContentCatalogRepository;
import com.example.adagent.data.GlobalCreativeRepository;
import com.example.adagent.data.dto.CampaignBase;
import com.example.adagent.data.dto.ContentItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CreativeCatalogService {

    private final AdDataRepository adDataRepository;
    private final GlobalCreativeRepository globalCreativeRepository;
    private final ContentCatalogRepository contentCatalogRepository;

    public CreativeCatalogService(AdDataRepository adDataRepository,
                                  GlobalCreativeRepository globalCreativeRepository,
                                  ContentCatalogRepository contentCatalogRepository) {
        this.adDataRepository = adDataRepository;
        this.globalCreativeRepository = globalCreativeRepository;
        this.contentCatalogRepository = contentCatalogRepository;
    }

    /**
     * 将全局素材 ID 绑定到广告组列表末尾（或 prepend=true 时插到队首）。顺序表示优先级。
     */
    public void bindCreativeToAdGroup(String userId, String campaignId, String adGroupId, String creativeId,
                                      boolean prepend) {
        if (!StringUtils.hasText(creativeId)) {
            throw new IllegalArgumentException("creativeId 不能为空");
        }
        if (!globalCreativeRepository.exists(userId, creativeId.trim())) {
            throw new IllegalArgumentException("素材不存在: " + creativeId);
        }
        CampaignBase base = adDataRepository.loadBase(userId);
        CampaignBase.Campaign campaign = base.getCampaigns().stream()
                .filter(c -> campaignId != null && campaignId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到计划: " + campaignId));
        CampaignBase.AdGroup ag = campaign.getAdGroups().stream()
                .filter(g -> adGroupId != null && adGroupId.equals(g.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到广告组: " + adGroupId));
        List<String> ids = new ArrayList<>(ag.getCreativeIds());
        String cid = creativeId.trim();
        ids.remove(cid);
        if (prepend) {
            ids.add(0, cid);
        } else {
            ids.add(cid);
        }
        ag.setCreativeIds(ids);
        adDataRepository.saveBase(userId, base);
    }

    public ContentItem createContent(String userId, String name, String summary) {
        ContentItem item = new ContentItem();
        item.setId(ContentCatalogRepository.newContentId());
        item.setName(name != null ? name.trim() : "");
        item.setSummary(summary != null ? summary.trim() : "");
        contentCatalogRepository.upsert(userId, item);
        return item;
    }

    /** 某计划下所有广告组引用的素材 ID（去重保序） */
    public Set<String> collectCreativeIdsForCampaign(CampaignBase.Campaign campaign) {
        Set<String> ids = new LinkedHashSet<>();
        if (campaign.getAdGroups() == null) {
            return ids;
        }
        for (CampaignBase.AdGroup ag : campaign.getAdGroups()) {
            if (ag.getCreativeIds() != null) {
                ids.addAll(ag.getCreativeIds());
            }
        }
        return ids;
    }

    public Map<String, com.example.adagent.data.dto.GlobalCreative> resolveCreativesForCampaign(String userId,
                                                                                                  CampaignBase.Campaign campaign) {
        return globalCreativeRepository.findByIds(userId, collectCreativeIdsForCampaign(campaign));
    }
}
