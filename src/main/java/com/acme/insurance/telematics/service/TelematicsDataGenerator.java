package com.acme.insurance.telematics.service;

import com.acme.insurance.telematics.model.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TelematicsDataGenerator {

    private final SecureRandom random = new SecureRandom();

    public TelematicsMessage generateTelematicsData(Driver driver) {
        switch (driver.getCurrentState()) {
            case DRIVING -> {
                return generateDrivingData(driver);
            }
            case PARKED, TRAFFIC_STOP, BREAK_TIME, POST_CRASH_IDLE -> {
                return generateStationaryData(driver);
            }
            default -> {
                return generateDrivingData(driver);
            }
        }
    }

    public TelematicsMessage generateCrashEventData(Driver driver) {
        GpsData gps = new GpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude()
        );

        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals(5.0 + random.nextDouble() * 3.0),
            roundToFourDecimals(4.0 + random.nextDouble() * 3.0),
            roundToFourDecimals((random.nextDouble() - 0.5) * 4.0)
        );

        return new TelematicsMessage(
            driver.getPolicyId(),
            Instant.now(),
            driver.getCurrentSpeed(),
            new SensorData(gps, accelerometer),
            roundToTwoDecimals(calculateGForce(accelerometer))
        );
    }

    private TelematicsMessage generateDrivingData(Driver driver) {
        GpsData gps = new GpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude()
        );

        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.0),
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.0),
            roundToFourDecimals(0.8 + random.nextDouble() * 0.4)
        );

        return new TelematicsMessage(
            driver.getPolicyId(),
            Instant.now(),
            roundToTwoDecimals(driver.getCurrentSpeed()),
            new SensorData(gps, accelerometer),
            roundToTwoDecimals(calculateGForce(accelerometer))
        );
    }

    private TelematicsMessage generateStationaryData(Driver driver) {
        GpsData gps = new GpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude()
        );

        // Very low accelerometer readings for stationary vehicle
        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.1), // Minimal vibration
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.1),
            roundToFourDecimals(0.98 + random.nextDouble() * 0.04)  // Nearly 1G downward
        );

        return new TelematicsMessage(
            driver.getPolicyId(),
            Instant.now(),
            0.0, // Zero speed when stationary
            new SensorData(gps, accelerometer),
            roundToTwoDecimals(calculateGForce(accelerometer))
        );
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
    
    private double calculateGForce(AccelerometerData accel) {
        return Math.sqrt(accel.x() * accel.x() + accel.y() * accel.y() + accel.z() * accel.z());
    }
}