package com.bryant.notifications.queue;

import com.bryant.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfiguration {
    public static final String EXCHANGE = "notifications.exchange";
    public static final String QUEUE = "notifications.queue";
    public static final String ROUTING_KEY = "notifications.send";
    public static final String DEAD_LETTER_EXCHANGE = "notifications.dlx";
    public static final String DEAD_LETTER_QUEUE = "notifications.dead-letter";
    public static final String DEAD_LETTER_ROUTING_KEY = "notifications.failed";

    @Bean
    DirectExchange notificationExchange() { return new DirectExchange(EXCHANGE, true, false); }

    @Bean
    DirectExchange deadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue deadLetterQueue() { return QueueBuilder.durable(DEAD_LETTER_QUEUE).build(); }

    @Bean
    Binding notificationBinding() { return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(ROUTING_KEY); }

    @Bean
    Binding deadLetterBinding() { return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY); }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    MessageRecoverer notificationMessageRecoverer(NotificationService service, ObjectMapper objectMapper) {
        return (message, cause) -> {
            try {
                NotificationMessage notification = objectMapper.readValue(message.getBody(), NotificationMessage.class);
                service.markFailed(notification.notificationId(), "Delivery failed after retries: " + cause.getMessage());
            } catch (Exception recoveryError) {
                // The broker will still dead-letter the malformed or unrecoverable message.
            }
            throw new AmqpRejectAndDontRequeueException("Notification delivery exhausted retries", cause);
        };
    }

    @Bean
    Advice notificationRetryAdvice(MessageRecoverer notificationMessageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(notificationMessageRecoverer)
                .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            Advice notificationRetryAdvice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(notificationRetryAdvice);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
