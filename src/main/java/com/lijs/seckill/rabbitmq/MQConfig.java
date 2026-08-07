package com.lijs.seckill.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MQConfig {

    public static final String QUEUE = "queue";

    /** 秒杀主队列 */
    public static final String SECKILL = "seckill.queue";

    /** 死信交换机 & 死信队列 */
    public static final String SECKILL_DLX      = "seckill.dead.exchange";
    public static final String SECKILL_DLQ      = "seckill.dead.queue";
    public static final String SECKILL_DLQ_KEY  = "seckill.dead.routingKey";

    /**
     * 秒杀主队列（持久化），绑定死信交换机：
     * 当消费者 basicNack(requeue=false) 时，消息自动路由至死信队列。
     */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX)
                .withArgument("x-dead-letter-routing-key", SECKILL_DLQ_KEY)
                .build();
    }

    /** 死信交换机（Direct 模式） */
    @Bean
    public DirectExchange seckillDeadExchange() {
        return new DirectExchange(SECKILL_DLX, true, false);
    }

    /** 死信队列（持久化），用于人工/定时任务对账补偿 */
    @Bean
    public Queue seckillDeadQueue() {
        return QueueBuilder.durable(SECKILL_DLQ).build();
    }

    /** 死信队列绑定死信交换机 */
    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder.bind(seckillDeadQueue())
                .to(seckillDeadExchange())
                .with(SECKILL_DLQ_KEY);
    }
}

