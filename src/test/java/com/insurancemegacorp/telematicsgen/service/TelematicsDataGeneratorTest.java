package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.AccidentType;
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
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
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
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
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
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.POST_CRASH_IDLE);

        FlatTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(0.0);
        assertThat(message.eventTime()).isNotNull();
        assertThat(testDriver.isStationary()).isTrue();
    }

    @Test
    void generateCrashEventData_shouldReturnCrashMessageWithAccidentType() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentSpeed(35.0);

        FlatTelematicsMessage message = dataGenerator.generateCrashEventData(testDriver);

        assertThat(message.policyId()).isEqualTo(200123);
        assertThat(message.speedMph()).isEqualTo(35.0); // Speed at moment of impact
        assertThat(message.eventTime()).isNotNull();
        assertThat(message.gpsLatitude()).isEqualTo(40.7128);
        assertThat(message.gpsLongitude()).isEqualTo(-74.0060);
        // Accident type should be set
        assertThat(message.accidentType()).isNotNull();
        // Should be a valid AccidentType name
        assertThat(AccidentType.valueOf(message.accidentType())).isNotNull();
    }

    @Test
    void generateCrashEventData_withSpecificType_shouldHaveCharacteristicSensorData() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentSpeed(35.0);

        // Test T-bone collision - should have high lateral (Y) acceleration
        FlatTelematicsMessage tBoneMessage = dataGenerator.generateCrashEventData(testDriver, AccidentType.T_BONE);
        assertThat(tBoneMessage.accidentType()).isEqualTo("T_BONE");
        assertThat(tBoneMessage.accelerometerY()).isGreaterThan(4.0); // High lateral G

        // Test rollover - should have extreme gyroscope readings
        FlatTelematicsMessage rolloverMessage = dataGenerator.generateCrashEventData(testDriver, AccidentType.ROLLOVER);
        assertThat(rolloverMessage.accidentType()).isEqualTo("ROLLOVER");
        // Rollover has extreme gyroscope Y (roll): 6.0 to 12.0
        assertThat(Math.abs(rolloverMessage.gyroscopeY())).isGreaterThan(5.0);
    }

    @Test
    void generateDrivingData_shouldNotHaveAccidentType() {
        TelematicsDataGenerator dataGenerator = new TelematicsDataGenerator();
        Driver testDriver = new Driver(999001, 200123, 300999, "1HGBH41JXMN109999", 40.7128, -74.0060);
        testDriver.setCurrentState(DriverState.DRIVING);
        testDriver.setCurrentSpeed(30.0);

        FlatTelematicsMessage message = dataGenerator.generateTelematicsData(testDriver);

        assertThat(message.accidentType()).isNull();
    }
}