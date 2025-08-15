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
        FlatTelematicsMessage message = createTestMessage();
        Driver driver = new Driver("test-driver", 1, 1, "vin", 0, 0);
        
        publisher.publishTelematicsData(message, driver);
        
        verify(rabbitTemplate).convertAndSend(eq("telematics_work_queue.crash-detection-group"), eq(""), eq(message));
    }

    @Test
    void publishTelematicsData_shouldThrowExceptionOnFailure() {
        FlatTelematicsMessage message = createTestMessage();
        Driver driver = new Driver("test-driver", 1, 1, "vin", 0, 0);
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(FlatTelematicsMessage.class));
        
        assertThrows(RuntimeException.class, () -> publisher.publishTelematicsData(message, driver));
    }

    private FlatTelematicsMessage createTestMessage() {
        return new FlatTelematicsMessage(
            // Core message fields
            200123,              // policyId
            300999,              // vehicleId
            "TEST-VIN-123456789", // vin
            Instant.now(),       // eventTime
            30.0,                // speedMph
            25,                  // speedLimitMph
            1.0,                 // gForce
            "400123",            // driverId
            "Test Street",       // currentStreet
            
            // GPS data fields
            40.7128,             // gpsLatitude
            -74.0060,            // gpsLongitude
            100.0,               // gpsAltitude
            5.0,                 // gpsSpeed
            45.0,                // gpsBearing
            3.0,                 // gpsAccuracy
            8,                   // gpsSatelliteCount
            1500L,               // gpsFixTime
            
            // Accelerometer data fields
            0.1,                 // accelerometerX
            0.2,                 // accelerometerY
            0.9,                 // accelerometerZ
            
            // Gyroscope data fields
            0.01,                // gyroscopeX
            0.02,                // gyroscopeY
            0.03,                // gyroscopeZ
            
            // Magnetometer data fields
            25.0,                // magnetometerX
            30.0,                // magnetometerY
            35.0,                // magnetometerZ
            180.0,               // magnetometerHeading
            
            // Environmental data
            1013.25,             // barometricPressure
            
            // Device metadata fields
            95,                  // deviceBatteryLevel
            -70,                 // deviceSignalStrength
            "portrait",          // deviceOrientation
            true,                // deviceScreenOn
            false                // deviceCharging
        );
    }
}