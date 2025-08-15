package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
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

        EnhancedTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(30.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        assertThat(message.sensors().accelerometer().x()).isBetween(-0.5, 0.5);
        assertThat(message.sensors().accelerometer().y()).isBetween(-0.5, 0.5);
        assertThat(message.sensors().accelerometer().z()).isBetween(0.8, 1.2);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForParkedState() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.PARKED);

        EnhancedTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        // Stationary accelerometer readings should be very low
        assertThat(message.sensors().accelerometer().x()).isBetween(-0.05, 0.05);
        assertThat(message.sensors().accelerometer().y()).isBetween(-0.05, 0.05);
        assertThat(message.sensors().accelerometer().z()).isBetween(0.98, 1.02);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForPostCrashIdle() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver("TEST-001", 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.POST_CRASH_IDLE);

        EnhancedTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

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

        EnhancedTelematicsMessage message = dataGenerator.generateCrashEventData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0); // Speed is zero during crash event
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        assertThat(message.sensors().accelerometer().x()).isGreaterThan(4.0);
        assertThat(message.sensors().accelerometer().y()).isGreaterThan(3.0);
        assertThat(message.sensors().accelerometer().z()).isBetween(-2.0, 2.0);
    }
}