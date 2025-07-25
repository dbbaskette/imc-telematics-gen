package com.insurancemegacorp.telematicsgen.integration;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
import com.insurancemegacorp.telematicsgen.service.TelematicsDataGenerator;
import com.insurancemegacorp.telematicsgen.service.TelematicsPublisher;

import org.junit.jupiter.api.Test;

// RabbitTemplate no longer needed - using Spring Cloud Stream
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

    // RabbitTemplate removed - using Spring Cloud Stream bindings



    @Test
    void shouldPublishDrivingData() throws Exception {
        Driver testDriver = new Driver("TEST-001", "TEST-POLICY-123", "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.DRIVING);
        testDriver.setCurrentSpeed(30.0);
        
        EnhancedTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);
        
        // Test that publishing doesn't throw an exception
        assertThat(message).isNotNull();
        assertThat(message.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(message.speedMph()).isEqualTo(30.0);
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        
        // Verify publishing works without exception
        publisher.publishTelematicsData(message);
    }

    @Test
    void shouldPublishCrashEventData() throws Exception {
        Driver testDriver = new Driver("TEST-001", "TEST-POLICY-123", "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentSpeed(35.0);
        
        EnhancedTelematicsMessage crashMessage = dataGenerator.generateCrashEventData(testDriver);
        
        // Test crash event data generation
        assertThat(crashMessage).isNotNull();
        assertThat(crashMessage.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(crashMessage.speedMph()).isEqualTo(35.0);
        assertThat(crashMessage.sensors().accelerometer().x()).isGreaterThan(4.0);
        assertThat(crashMessage.sensors().accelerometer().y()).isGreaterThan(3.0);
        
        // Verify publishing works without exception
        publisher.publishTelematicsData(crashMessage);
    }
}