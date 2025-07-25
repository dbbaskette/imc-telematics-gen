package com.insurancemegacorp.telematicsgen.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${telematics.queue.name:telematics_work_queue.crash-detection-group}")
    private String queueName;

    @Bean
    public Queue telematicsWorkQueue() {
        return QueueBuilder.durable(queueName).build();
    }
}
