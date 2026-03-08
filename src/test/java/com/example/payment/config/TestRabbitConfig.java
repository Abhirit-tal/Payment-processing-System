package com.example.payment.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock RabbitMQ beans.
 *
 * Since tests exclude RabbitAutoConfiguration, we need to provide
 * a mock RabbitTemplate for the PaymentEventQueue to use.
 */
@TestConfiguration
public class TestRabbitConfig {

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        return mock(RabbitTemplate.class);
    }
}

