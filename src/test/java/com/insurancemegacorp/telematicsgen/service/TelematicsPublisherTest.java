package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;


import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelematicsPublisherTest {

    private static final String TEST_EXCHANGE_NAME = "telematics_work_queue.crash-detection-group";

    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @Mock
    private WebSocketBroadcastService webSocketService;

    private TelematicsPublisher publisher;
    
    @BeforeEach
    void setUp() {
        publisher = new TelematicsPublisher(rabbitTemplate, webSocketService);
        ReflectionTestUtils.setField(publisher, "exchangeName", TEST_EXCHANGE_NAME);
    }

    @Test
    void publishTelematicsData_shouldSendMessageToStream() {
        EnhancedTelematicsMessage message = createTestMessage();
        Driver driver = new Driver("test-driver", 1, 1, "vin", 0, 0);
        
        publisher.publishTelematicsData(message, driver);
        
        verify(rabbitTemplate).convertAndSend(eq("telematics_work_queue.crash-detection-group"), eq(""), eq(message));
    }

    @Test
    void publishTelematicsData_shouldThrowExceptionOnFailure() {
        EnhancedTelematicsMessage message = createTestMessage();
        Driver driver = new Driver("test-driver", 1, 1, "vin", 0, 0);
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(EnhancedTelematicsMessage.class));
        
        assertThrows(RuntimeException.class, () -> publisher.publishTelematicsData(message, driver));
    }

    private EnhancedTelematicsMessage createTestMessage() {
        EnhancedGpsData gps = new EnhancedGpsData(40.7128, -74.0060, 100.0, 5.0, 45.0, 3.0, 8, 1500L);
        AccelerometerData accel = new AccelerometerData(0.1, 0.2, 0.9);
        GyroscopeData gyro = new GyroscopeData(0.01, 0.02, 0.03);
        MagnetometerData mag = new MagnetometerData(25.0, 30.0, 35.0, 180.0);
        DeviceMetadata device = new DeviceMetadata(95, -70, "portrait", true, false);
        EnhancedSensorData sensors = new EnhancedSensorData(gps, accel, gyro, mag, 1013.25, device);
        
        return new EnhancedTelematicsMessage(
            200123,              // policyId
            300999,              // vehicleId
            "TEST-VIN-123456789", // vin
            Instant.now(),       // eventTime
            false,               // isCrashEvent
            gps,                 // gps
            30.0,                // speedMph
            25,                  // speedLimitMph
            sensors,             // sensors
            1.0,                 // gForce
            "DRIVER-400123",     // driverId
            "Test Street"        // currentStreet
        );
    }
}