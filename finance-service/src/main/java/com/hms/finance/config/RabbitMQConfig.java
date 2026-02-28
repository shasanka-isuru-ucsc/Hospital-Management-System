package com.hms.finance.config;

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

    // Billing exchange — published by Clinical / Ward / Lab services
    public static final String BILLING_EXCHANGE = "billing.events";

    public static final String BILLING_OPD_ROUTING_KEY = "billing.opd";
    public static final String BILLING_WOUND_ROUTING_KEY = "billing.wound";
    public static final String BILLING_WARD_ROUTING_KEY = "billing.ward";
    public static final String BILLING_LAB_ROUTING_KEY = "billing.lab";

    public static final String BILLING_OPD_QUEUE = "billing.opd.queue";
    public static final String BILLING_WOUND_QUEUE = "billing.wound.queue";
    public static final String BILLING_WARD_QUEUE = "billing.ward.queue";
    public static final String BILLING_LAB_QUEUE = "billing.lab.queue";

    @Bean
    public TopicExchange billingExchange() {
        return new TopicExchange(BILLING_EXCHANGE);
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
    public Queue billingWardQueue() {
        return new Queue(BILLING_WARD_QUEUE, true);
    }

    @Bean
    public Queue billingLabQueue() {
        return new Queue(BILLING_LAB_QUEUE, true);
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
    public Binding billingWardBinding(Queue billingWardQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingWardQueue).to(billingExchange).with(BILLING_WARD_ROUTING_KEY);
    }

    @Bean
    public Binding billingLabBinding(Queue billingLabQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingLabQueue).to(billingExchange).with(BILLING_LAB_ROUTING_KEY);
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
