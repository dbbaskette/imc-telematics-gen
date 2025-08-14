package com.insurancemegacorp.telematicsgen.config;

import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${telematics.exchange.name:telematics_exchange}")
    private String exchangeName;

    @Bean
    public Exchange telematicsExchange() {
        return ExchangeBuilder.fanoutExchange(exchangeName).durable(true).build();
    }
}
