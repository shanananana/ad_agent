package com.example.adagent.tools;

import com.example.adagent.data.AdDataRepository;
import com.example.adagent.data.GlobalCreativeRepository;
import com.example.adagent.data.PerformanceDataRepository;
import com.example.adagent.data.dto.CampaignBase;
import com.example.adagent.data.dto.GlobalCreative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI <strong>写操作工具</strong>：新建计划、调整预算与启停等，调用后更新 {@code campaigns.json} 并按规则生成/更新
 * 对应 {@code performance.json} 明细（学习项目中的本地模拟，生产需审批与真实投放 API）。
 */
@Component
public class CampaignMutationTools {

    private static final Logger logger = LoggerFactory.getLogger(CampaignMutationTools.class);
    private final AdDataRepository adDataRepository;
    private final PerformanceDataRepository performanceDataRepository;
    private final GlobalCreativeRepository globalCreativeRepository;

    public CampaignMutationTools(AdDataRepository adDataRepository,
                                 PerformanceDataRepository performanceDataRepository,
                                 GlobalCreativeRepository globalCreativeRepository) {
        this.adDataRepository = adDataRepository;
        this.performanceDataRepository = performanceDataRepository;
        this.globalCreativeRepository = globalCreativeRepository;
    }

    @Tool(description = "新增一个投放计划。当用户说「加一个投放计划」「新建计划」「创建计划」时使用。参数 userId 当前用户ID（多用户时必传以隔离效果数据）；name 为计划名称；dailyBudget 为日预算（元），可选。会自动创建默认广告组、广告、素材，并立即为该用户生成效果数据写入本地。")
    public String addCampaign(String userId, String name, Double dailyBudget) {
        try {
            CampaignBase base = adDataRepository.loadBase(userId);
            String cId = adDataRepository.nextCampaignId(userId);
            CampaignBase.Campaign campaign = new CampaignBase.Campaign();
            campaign.setId(cId);
            campaign.setName(name != null && !name.isBlank() ? name.trim() : "计划-" + cId);
            campaign.setStatus("ACTIVE");
            campaign.setDailyBudget(dailyBudget != null && dailyBudget > 0 ? dailyBudget : 500.0);
            campaign.setStartDate(java.time.LocalDate.now().toString());
            campaign.setEndDate(java.time.LocalDate.now().plusMonths(1).toString());

            String agId = adDataRepository.nextAdGroupId(campaign.getAdGroups());
            CampaignBase.AdGroup ag = new CampaignBase.AdGroup();
            ag.setId(agId);
            ag.setName("默认广告组");
            ag.setStatus("ACTIVE");
            CampaignBase.Targeting targeting = new CampaignBase.Targeting();
            targeting.setChannels(java.util.List.of("APP", "H5"));
            targeting.setAgeRanges(java.util.List.of("18-24", "25-34"));
            ag.setTargeting(targeting);

            String crId = GlobalCreativeRepository.newCreativeId();
            GlobalCreative cr = new GlobalCreative();
            cr.setId(crId);
            cr.setType("IMAGE");
            cr.setTitle("默认素材");
            cr.setStatus("APPROVED");
            globalCreativeRepository.upsert(userId, cr);
            ag.setCreativeIds(java.util.List.of(crId));
            ag.setAds(new java.util.ArrayList<>());
            campaign.getAdGroups().add(ag);
            base.getCampaigns().add(campaign);

            adDataRepository.saveBase(userId, base);
            performanceDataRepository.generateAndAppendForCampaign(cId, userId);
            logger.info("【工具】已新增计划 {} 并写入基础数据与效果数据", cId);
            return "已成功创建投放计划：" + campaign.getId() + "（" + campaign.getName() + "），日预算 " + campaign.getDailyBudget() + " 元，并已生成最近 7 天效果数据。";
        } catch (Exception e) {
            logger.error("addCampaign 失败", e);
            return "创建计划失败：" + e.getMessage();
        }
    }

    @Tool(description = "调整投放策略：修改预算、定向、启停等。当用户说「改预算」「暂停计划」「调高日预算」等时使用。参数 userId 当前用户ID（多用户时必传）；campaignId 必填；dailyBudget 新日预算；status 为 ACTIVE/PAUSED。修改后立即写回本地并为该用户重新生成该计划的效果数据。")
    public String adjustStrategy(String userId, String campaignId, Double dailyBudget, String status) {
        try {
            CampaignBase base = adDataRepository.loadBase(userId);
            CampaignBase.Campaign campaign = base.getCampaigns().stream()
                    .filter(c -> campaignId != null && campaignId.equals(c.getId()))
                    .findFirst()
                    .orElse(null);
            if (campaign == null) {
                return "未找到计划 ID：" + campaignId;
            }
            if (dailyBudget != null && dailyBudget > 0) {
                campaign.setDailyBudget(dailyBudget);
            }
            if (status != null && ("ACTIVE".equalsIgnoreCase(status) || "PAUSED".equalsIgnoreCase(status))) {
                campaign.setStatus(status.toUpperCase());
            }
            adDataRepository.saveBase(userId, base);
            performanceDataRepository.generateAndAppendForCampaign(campaignId, userId);
            return "已更新计划 " + campaignId + " 的策略并重新生成效果数据。";
        } catch (Exception e) {
            logger.error("adjustStrategy 失败", e);
            return "策略调整失败：" + e.getMessage();
        }
    }
}
