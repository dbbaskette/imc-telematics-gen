package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;


import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelematicsPublisherTest {

    @Mock
    private StreamBridge streamBridge;
    
    @Mock
    private WebSocketBroadcastService webSocketService;

    @InjectMocks
    private TelematicsPublisher publisher;

    @Test
    void publishTelematicsData_shouldSendMessageToStream() {
        EnhancedTelematicsMessage message = createTestMessage();
        
        publisher.publishTelematicsData(message);
        
        verify(streamBridge).send(eq("telematics-out"), eq(message));
    }

    @Test
    void publishTelematicsData_shouldThrowExceptionOnFailure() {
        EnhancedTelematicsMessage message = createTestMessage();
        doThrow(new RuntimeException("Connection failed"))
            .when(streamBridge).send(anyString(), any(EnhancedTelematicsMessage.class));
        
        assertThrows(RuntimeException.class, () -> publisher.publishTelematicsData(message));
    }

    private EnhancedTelematicsMessage createTestMessage() {
        EnhancedGpsData gps = new EnhancedGpsData(40.7128, -74.0060, 100.0, 5.0, 45.0, 3.0, 8, 1500L);
        AccelerometerData accel = new AccelerometerData(0.1, 0.2, 0.9);
        GyroscopeData gyro = new GyroscopeData(0.01, 0.02, 0.03);
        MagnetometerData mag = new MagnetometerData(25.0, 30.0, 35.0, 180.0);
        DeviceMetadata device = new DeviceMetadata(95, -70, "portrait", true, false);
        EnhancedSensorData sensors = new EnhancedSensorData(gps, accel, gyro, mag, 1013.25, device);
        
        return new EnhancedTelematicsMessage(
            "TEST-POLICY-123",
            "TEST-VIN-123456789",
            Instant.now(),
            30.0,
            "Test Street",
            1.0, // Test G-force
            sensors
        );
    }
}