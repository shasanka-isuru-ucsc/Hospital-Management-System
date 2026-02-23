package com.hms.clinical.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Billing exchange & queues
    public static final String BILLING_EXCHANGE = "billing.events";
    public static final String BILLING_OPD_ROUTING_KEY = "billing.opd";
    public static final String BILLING_WOUND_ROUTING_KEY = "billing.wound";
    public static final String BILLING_OPD_QUEUE = "billing.opd.queue";
    public static final String BILLING_WOUND_QUEUE = "billing.wound.queue";

    // Lab exchange & queues
    public static final String LAB_EXCHANGE = "lab.events";
    public static final String LAB_REQUESTED_ROUTING_KEY = "lab.requested";
    public static final String LAB_REQUESTED_QUEUE = "lab.requested.queue";

    @Bean
    public TopicExchange billingExchange() {
        return new TopicExchange(BILLING_EXCHANGE);
    }

    @Bean
    public TopicExchange labExchange() {
        return new TopicExchange(LAB_EXCHANGE);
    }

    @Bean
    public Queue billingOpdQueue() {
        return new Queue(BILLING_OPD_QUEUE, true);
    }

    @Bean
    public Queue billingWoundQueue() {
        return new Queue(BILLING_WOUND_QUEUE, true);
    }

    @Bean
    public Queue labRequestedQueue() {
        return new Queue(LAB_REQUESTED_QUEUE, true);
    }

    @Bean
    public Binding billingOpdBinding(Queue billingOpdQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingOpdQueue).to(billingExchange).with(BILLING_OPD_ROUTING_KEY);
    }

    @Bean
    public Binding billingWoundBinding(Queue billingWoundQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingWoundQueue).to(billingExchange).with(BILLING_WOUND_ROUTING_KEY);
    }

    @Bean
    public Binding labRequestedBinding(Queue labRequestedQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(labRequestedQueue).to(labExchange).with(LAB_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
