package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.TelematicsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TelematicsDataGenerator.class)
class TelematicsDataGeneratorTest {

    @Test
    void generateTelematicsData_shouldReturnDrivingDataForDrivingState() {
        TelematicsDataGenerator generator = new TelematicsDataGenerator();
        Driver driver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        driver.setCurrentState(DriverState.DRIVING);
        driver.setCurrentSpeed(30.0);

        TelematicsMessage message = generator.generateTelematicsData(driver);

        assertThat(message.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(message.speedMph()).isEqualTo(30.0);
        assertThat(message.timestamp()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        assertThat(message.sensors().accelerometer().x()).isBetween(-0.5, 0.5);
        assertThat(message.sensors().accelerometer().y()).isBetween(-0.5, 0.5);
        assertThat(message.sensors().accelerometer().z()).isBetween(0.8, 1.2);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForParkedState() {
        TelematicsDataGenerator generator = new TelematicsDataGenerator();
        Driver driver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        driver.setCurrentState(DriverState.PARKED);

        TelematicsMessage message = generator.generateTelematicsData(driver);

        assertThat(message.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.timestamp()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        // Stationary accelerometer readings should be very low
        assertThat(message.sensors().accelerometer().x()).isBetween(-0.05, 0.05);
        assertThat(message.sensors().accelerometer().y()).isBetween(-0.05, 0.05);
        assertThat(message.sensors().accelerometer().z()).isBetween(0.98, 1.02);
    }

    @Test
    void generateTelematicsData_shouldReturnStationaryDataForPostCrashIdle() {
        TelematicsDataGenerator generator = new TelematicsDataGenerator();
        Driver driver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        driver.setCurrentState(DriverState.POST_CRASH_IDLE);

        TelematicsMessage message = generator.generateTelematicsData(driver);

        assertThat(message.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.timestamp()).isNotNull();
        assertThat(driver.isStationary()).isTrue();
    }

    @Test
    void generateCrashEventData_shouldReturnCrashMessage() {
        TelematicsDataGenerator generator = new TelematicsDataGenerator();
        Driver driver = new Driver("TEST-001", "TEST-POLICY-123", 40.7128, -74.0060);
        driver.setCurrentSpeed(35.0);

        TelematicsMessage message = generator.generateCrashEventData(driver);

        assertThat(message.policyId()).isEqualTo("TEST-POLICY-123");
        assertThat(message.speedMph()).isEqualTo(35.0);
        assertThat(message.timestamp()).isNotNull();
        assertThat(message.sensors().gps().latitude()).isEqualTo(40.7128);
        assertThat(message.sensors().gps().longitude()).isEqualTo(-74.0060);
        assertThat(message.sensors().accelerometer().x()).isGreaterThan(4.0);
        assertThat(message.sensors().accelerometer().y()).isGreaterThan(3.0);
        assertThat(message.sensors().accelerometer().z()).isBetween(-2.0, 2.0);
    }
}