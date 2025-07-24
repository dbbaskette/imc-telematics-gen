package com.insurancemegacorp.telematicsgen.integration;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.TelematicsMessage;
import com.insurancemegacorp.telematicsgen.service.TelematicsDataGenerator;
import com.insurancemegacorp.telematicsgen.service.TelematicsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class TelematicsIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private TelematicsDataGenerator dataGenerator;

    @Autowired
    private TelematicsPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPublishAndReceiveDrivingData() throws Exception {
        String queueName = "test_telematics_stream";
        
        Driver testDriver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.DRIVING);
        testDriver.setCurrentSpeed(30.0);
        
        TelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);
        publisher.publishTelematicsData(message);
        
        Message receivedMessage = rabbitTemplate.receive(queueName, 5000);
        assertThat(receivedMessage).isNotNull();
        
        TelematicsMessage deserializedMessage = objectMapper.readValue(
            receivedMessage.getBody(), TelematicsMessage.class);
        
        assertThat(deserializedMessage.policyId()).isEqualTo(message.policyId());
        assertThat(deserializedMessage.speedMph()).isEqualTo(message.speedMph());
        assertThat(deserializedMessage.sensors().gps().latitude())
            .isEqualTo(message.sensors().gps().latitude());
        assertThat(deserializedMessage.sensors().accelerometer().x())
            .isEqualTo(message.sensors().accelerometer().x());
    }

    @Test
    void shouldPublishAndReceiveCrashEventData() throws Exception {
        String queueName = "test_telematics_stream";
        
        Driver testDriver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        testDriver.setCurrentSpeed(35.0);
        
        TelematicsMessage crashMessage = dataGenerator.generateCrashEventData(testDriver);
        publisher.publishTelematicsData(crashMessage);
        
        Message receivedMessage = rabbitTemplate.receive(queueName, 5000);
        assertThat(receivedMessage).isNotNull();
        
        TelematicsMessage deserializedMessage = objectMapper.readValue(
            receivedMessage.getBody(), TelematicsMessage.class);
        
        assertThat(deserializedMessage.policyId()).isEqualTo(crashMessage.policyId());
        assertThat(deserializedMessage.speedMph()).isEqualTo(35.0);
        assertThat(deserializedMessage.sensors().accelerometer().x()).isGreaterThan(4.0);
        assertThat(deserializedMessage.sensors().accelerometer().y()).isGreaterThan(3.0);
    }
}