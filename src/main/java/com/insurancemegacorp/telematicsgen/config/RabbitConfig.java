package com.insurancemegacorp.telematicsgen.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        
        // Enable publisher confirms for reliability
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // Log failed confirms for monitoring
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                    .warn("Message publish failed: {}", cause);
            }
        });
        
        // Enable mandatory flag for returns
        template.setMandatory(true);
        
        return template;
    }
}