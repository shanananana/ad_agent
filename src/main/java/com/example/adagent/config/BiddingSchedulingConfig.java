package com.example.adagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 定时任务调度，使 {@link com.example.adagent.bidding.BidCoefficientScheduledJob} 等
 * {@code @Scheduled} Bean 生效。
 */
@Configuration
@EnableScheduling
public class BiddingSchedulingConfig {
}
