package com.acme.insurance.telematics.service;

import com.acme.insurance.telematics.model.AccelerometerData;
import com.acme.insurance.telematics.model.GpsData;
import com.acme.insurance.telematics.model.SensorData;
import com.acme.insurance.telematics.model.TelematicsMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelematicsPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TelematicsPublisher publisher;

    @Test
    void publishTelematicsData_shouldSendMessageToQueue() {
        ReflectionTestUtils.setField(publisher, "queueName", "test_queue");
        
        TelematicsMessage message = createTestMessage();
        
        publisher.publishTelematicsData(message);
        
        verify(rabbitTemplate).convertAndSend(eq("test_queue"), eq(message));
    }

    @Test
    void publishTelematicsData_shouldThrowExceptionOnFailure() {
        ReflectionTestUtils.setField(publisher, "queueName", "test_queue");
        
        TelematicsMessage message = createTestMessage();
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), any(TelematicsMessage.class));
        
        assertThrows(RuntimeException.class, () -> publisher.publishTelematicsData(message));
    }

    private TelematicsMessage createTestMessage() {
        GpsData gps = new GpsData(40.7128, -74.0060);
        AccelerometerData accel = new AccelerometerData(0.1, 0.2, 0.9);
        SensorData sensors = new SensorData(gps, accel);
        
        return new TelematicsMessage(
            "TEST-POLICY-123",
            Instant.now(),
            30.0,
            sensors,
            1.0 // Test G-force
        );
    }
}