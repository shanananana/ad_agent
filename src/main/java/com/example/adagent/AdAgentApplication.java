package com.example.adagent;

import com.example.adagent.config.BiddingProperties;
import com.example.adagent.config.DataPathConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DataPathConfig.class, BiddingProperties.class})
public class AdAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdAgentApplication.class, args);
    }
}
