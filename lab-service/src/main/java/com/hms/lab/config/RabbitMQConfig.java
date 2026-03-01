package com.hms.lab.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ─── Lab Events Exchange (consumed from Clinical) ─────────────────────────────
    public static final String LAB_EXCHANGE = "lab.events";
    public static final String LAB_REQUESTED_ROUTING_KEY = "lab.requested";
    public static final String LAB_REQUESTED_QUEUE = "lab.requested.queue";

    // ─── Billing Events Exchange (published to Finance) ───────────────────────────
    public static final String BILLING_EXCHANGE = "billing.events";
    public static final String BILLING_LAB_ROUTING_KEY = "billing.lab";

    @Bean
    public TopicExchange labExchange() {
        return new TopicExchange(LAB_EXCHANGE);
    }

    @Bean
    public TopicExchange billingExchange() {
        return new TopicExchange(BILLING_EXCHANGE);
    }

    @Bean
    public Queue labRequestedQueue() {
        return new Queue(LAB_REQUESTED_QUEUE, true);
    }

    @Bean
    public Binding labRequestedBinding(Queue labRequestedQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(labRequestedQueue).to(labExchange).with(LAB_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
