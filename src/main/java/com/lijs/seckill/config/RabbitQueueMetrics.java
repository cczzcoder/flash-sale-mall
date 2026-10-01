package com.lijs.seckill.config;

import com.lijs.seckill.rabbitmq.MQConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

/** 仅为两个固定队列暴露深度，避免动态队列名造成指标基数膨胀。 */
@Component
public class RabbitQueueMetrics {

    public RabbitQueueMetrics(MeterRegistry registry, AmqpAdmin amqpAdmin) {
        register(registry, amqpAdmin, MQConfig.SECKILL);
        register(registry, amqpAdmin, MQConfig.SECKILL_DLQ);
    }

    private void register(MeterRegistry registry, AmqpAdmin admin, String queue) {
        Gauge.builder("seckill.rabbit.queue.depth", admin, value -> queueDepth(value, queue))
                .tag("queue", queue)
                .description("RabbitMQ ready message count")
                .register(registry);
    }

    private double queueDepth(AmqpAdmin admin, String queue) {
        try {
            QueueInformation info = admin.getQueueInfo(queue);
            return info == null ? -1D : info.getMessageCount();
        } catch (Exception e) {
            return -1D;
        }
    }
}
