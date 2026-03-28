package com.shanananana.adagent;

import com.shanananana.adagent.config.BiddingProperties;
import com.shanananana.adagent.config.DataPathConfig;
import com.shanananana.adagent.skills.MarkdownSkillsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 应用入口：装配对话投放 Agent、自动调价（B×α）、创意文生图与本地 JSON 数据层等全部组件。
 * <p>通过 {@link EnableConfigurationProperties} 绑定 {@link DataPathConfig}（数据根路径）与
 * {@link BiddingProperties}（定时调价任务参数）。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties({DataPathConfig.class, BiddingProperties.class, MarkdownSkillsProperties.class})
public class AdAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdAgentApplication.class, args);
    }
}
