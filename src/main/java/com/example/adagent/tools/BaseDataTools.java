package com.example.adagent.tools;

import com.example.adagent.data.AdDataRepository;
import com.example.adagent.data.dto.CampaignBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI <strong>基础数据查询工具</strong>：由主对话 {@link org.springframework.ai.chat.client.ChatClient} 调用，按 {@code userId}/{@code campaignId}
 * 读取本地 {@code campaigns.json}（及全局素材解析），返回计划树或指定节点详情。
 */
@Component
public class BaseDataTools {

    private static final Logger logger = LoggerFactory.getLogger(BaseDataTools.class);
    private final AdDataRepository adDataRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BaseDataTools(AdDataRepository adDataRepository) {
        this.adDataRepository = adDataRepository;
    }

    @Tool(description = "查询投放计划、广告组、广告、素材的列表或详情。当用户问「有哪些计划」「计划列表」「广告列表」「素材有哪些」等时使用。参数 userId 当前用户ID（多用户时必传以隔离数据）；campaignId 可选，传则返回该计划详情，不传则返回全部计划摘要。")
    public String queryBaseData(String userId, String campaignId) {
        try {
            CampaignBase base = adDataRepository.loadBase(userId);
            if (base.getCampaigns().isEmpty()) {
                return "当前暂无投放计划，可以说「加一个投放计划」创建。";
            }
            if (campaignId != null && !campaignId.trim().isEmpty()) {
                String cid = campaignId.trim();
                var opt = base.getCampaigns().stream()
                        .filter(c -> cid.equals(c.getId()))
                        .findFirst();
                if (opt.isEmpty()) {
                    return "未找到计划 ID：" + cid;
                }
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(opt.get());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("投放计划列表（共 ").append(base.getCampaigns().size()).append(" 个）：\n\n");
            for (CampaignBase.Campaign c : base.getCampaigns()) {
                sb.append("- 计划 ").append(c.getId()).append("：").append(c.getName())
                        .append("，状态=").append(c.getStatus())
                        .append("，日预算=").append(c.getDailyBudget() != null ? c.getDailyBudget() + "元" : "未设")
                        .append("，广告组数=").append(c.getAdGroups() != null ? c.getAdGroups().size() : 0).append("\n");
            }
            return sb.toString();
        } catch (JsonProcessingException e) {
            logger.error("queryBaseData 序列化失败", e);
            return "查询基础数据时出错：" + e.getMessage();
        } catch (Exception e) {
            logger.error("queryBaseData 失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
