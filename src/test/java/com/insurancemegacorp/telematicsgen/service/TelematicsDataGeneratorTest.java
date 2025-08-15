package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.FlatTelematicsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TelematicsDataGenerator.class)
class TelematicsDataGeneratorTest {

    @Test
    void generateTelematicsData_shouldReturnDrivingDataForDrivingState() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.DRIVING);
        testDriver.setCurrentSpeed(30.0);

        FlatTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(30.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.gpsLatitude()).isEqualTo(40.7128);
        assertThat(message.gpsLongitude()).isEqualTo(-74.0060);
        assertThat(message.accelerometerX()).isBetween(-0.5, 0.5);
        assertThat(message.accelerometerY()).isBetween(-0.5, 0.5);
        assertThat(message.accelerometerZ()).isBetween(0.8, 1.2);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForParkedState() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.PARKED);

        FlatTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.gpsLatitude()).isEqualTo(40.7128);
        assertThat(message.gpsLongitude()).isEqualTo(-74.0060);
        // Stationary accelerometer readings should be very low
        assertThat(message.accelerometerX()).isBetween(-0.05, 0.05);
        assertThat(message.accelerometerY()).isBetween(-0.05, 0.05);
        assertThat(message.accelerometerZ()).isBetween(0.95, 1.05);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForPostCrashIdle() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.POST_CRASH_IDLE);

        FlatTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(testDriver.isStationary()).isTrue();
    }

    @Test
    void generateCrashEventData_shouldReturnCrashMessage() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentSpeed(35.0);

        FlatTelematicsMessage message = dataGenerator.generateCrashEventData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0); // Speed is zero during crash event
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.gpsLatitude()).isEqualTo(40.7128);
        assertThat(message.gpsLongitude()).isEqualTo(-74.0060);
        assertThat(message.accelerometerX()).isGreaterThan(4.0);
        assertThat(message.accelerometerY()).isGreaterThan(3.0);
        assertThat(message.accelerometerZ()).isBetween(-2.0, 2.0);
    }
}