package com.shanananana.adagent.bidding;

import com.shanananana.adagent.config.BiddingProperties;
import com.shanananana.adagent.data.AdDataRepository;
import com.shanananana.adagent.data.dto.CampaignBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动调价<strong>定时任务</strong>：按 {@code ad-agent.bidding.cron} 触发，遍历配置用户下各计划（或全局计划列表），
 * 调用 {@link BidCoefficientJobService} 拉取/合成效果快照并由 LLM 更新 α；可通过 {@code ad-agent.bidding.enabled=false} 关闭。
 */
@Component
public class BidCoefficientScheduledJob {

    private static final Logger logger = LoggerFactory.getLogger(BidCoefficientScheduledJob.class);
    private final BidCoefficientJobService jobService;
    private final BiddingProperties biddingProperties;
    private final AdDataRepository adDataRepository;

    public BidCoefficientScheduledJob(
            BidCoefficientJobService jobService,
            BiddingProperties biddingProperties,
            AdDataRepository adDataRepository) {
        this.jobService = jobService;
        this.biddingProperties = biddingProperties;
        this.adDataRepository = adDataRepository;
    }

    @Scheduled(cron = "${ad-agent.bidding.cron}")
    public void runScheduledCoefficientUpdate() {
        if (!biddingProperties.isEnabled()) {
            return;
        }
        String scheduleUserId = biddingProperties.getScheduleUserId();
        String uid = scheduleUserId != null && !scheduleUserId.isBlank() ? scheduleUserId : null;
        try {
            CampaignBase base = adDataRepository.loadBase(uid);
            var campaigns = base.getCampaigns();
            if (campaigns == null || campaigns.isEmpty()) {
                jobService.runCoefficientUpdateJob(false);
                return;
            }
            for (CampaignBase.Campaign c : campaigns) {
                if (c.getId() == null || c.getId().isBlank()) {
                    continue;
                }
                try {
                    jobService.runCoefficientUpdateJob(false, c.getId(), c.getName());
                } catch (Exception ex) {
                    logger.error("【自动调价】计划 {} 定时任务失败", c.getId(), ex);
                }
            }
        } catch (Exception e) {
            logger.error("【自动调价】定时任务失败", e);
        }
    }
}
