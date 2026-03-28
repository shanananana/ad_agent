package com.shanananana.adagent.config;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * <strong>DashScope HTTP 读超时</strong>补偿配置：文生图等调用耗时较长时，避免默认 RestClient 过早超时。
 * <p>因 spring-ai-alibaba-dashscope 部分版本未把 {@code spring.ai.dashscope.read-timeout} 接到 {@code DashScopeApi}，
 * 此处通过全局 {@link RestClientCustomizer} 将读超时落到 {@link org.springframework.web.client.RestClient}。</p>
 */
@Configuration
@ConditionalOnClass(DashScopeConnectionProperties.class)
public class DashScopeRestClientTimeoutConfig {

    private static final int DEFAULT_READ_TIMEOUT_MS = 180_000;

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    RestClientCustomizer dashScopeCompatibleRestReadTimeout(
            ObjectProvider<DashScopeConnectionProperties> connectionProperties) {
        return builder -> {
            int readMs = Optional.ofNullable(connectionProperties.getIfAvailable())
                    .map(DashScopeConnectionProperties::getReadTimeout)
                    .filter(ms -> ms > 0)
                    .orElse(DEFAULT_READ_TIMEOUT_MS);
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofMillis(readMs));
            builder.requestFactory(factory);
        };
    }
}
