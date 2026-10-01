package com.lijs.seckill.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 应用级公共指标标签；只使用固定低基数字段。 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.application.name:seckill}") String application,
            @Value("${management.metrics.tags.environment:local}") String environment) {
        return registry -> registry.config().commonTags(
                "application", application, "environment", environment);
    }
}
