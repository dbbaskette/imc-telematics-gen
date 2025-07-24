package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.*;
import com.insurancemegacorp.telematicsgen.model.AccelerometerData;
import com.insurancemegacorp.telematicsgen.model.DeviceMetadata;
import com.insurancemegacorp.telematicsgen.model.EnhancedGpsData;
import com.insurancemegacorp.telematicsgen.model.EnhancedSensorData;
import com.insurancemegacorp.telematicsgen.model.GyroscopeData;
import com.insurancemegacorp.telematicsgen.model.MagnetometerData;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TelematicsDataGenerator {

    private final SecureRandom random = new SecureRandom();

    public EnhancedTelematicsMessage generateTelematicsData(Driver driver) {
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

    public EnhancedTelematicsMessage generateCrashEventData(Driver driver) {
        // Enhanced GPS data with additional fields
        EnhancedGpsData gps = new EnhancedGpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            driver.getCurrentSpeed() * 0.44704,    // speed in m/s (convert from mph)
            driver.getCurrentBearing(),            // bearing from driver
            2.0 + random.nextDouble() * 3.0,       // GPS accuracy
            8 + random.nextInt(4),                 // satellite count (8-11)
            100 + random.nextLong() % 200          // fix time in ms
        );

        // High G-force accelerometer data for crash
        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals(5.0 + random.nextDouble() * 3.0),
            roundToFourDecimals(4.0 + random.nextDouble() * 3.0),
            roundToFourDecimals((random.nextDouble() - 0.5) * 4.0)
        );

        // Extreme gyroscope readings during crash (vehicle rotation)
        GyroscopeData gyroscope = new GyroscopeData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 6.0), // High pitch rotation
            roundToFourDecimals((random.nextDouble() - 0.5) * 8.0), // High roll rotation  
            roundToFourDecimals((random.nextDouble() - 0.5) * 4.0)  // High yaw rotation
        );

        // Magnetometer data (compass/magnetic field)
        MagnetometerData magnetometer = new MagnetometerData(
            roundToFourDecimals(20.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(-15.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(40.0 + random.nextDouble() * 15.0),
            driver.getCurrentBearing() // Compass heading
        );

        // Device metadata
        DeviceMetadata device = new DeviceMetadata(
            75 + random.nextInt(20),               // Battery level (75-95%)
            -70 - random.nextInt(30),              // Signal strength (-70 to -100 dBm)
            "landscape",                           // Device orientation
            random.nextBoolean(),                  // Screen on/off
            random.nextBoolean()                   // Charging status
        );

        // Enhanced sensor data with all sensors
        EnhancedSensorData sensors = new EnhancedSensorData(
            gps,
            accelerometer,
            gyroscope,
            magnetometer,
            1010.0 + random.nextDouble() * 10.0, // Barometric pressure
            device
        );

        // Calculate G-force from accelerometer data
        double gForce = calculateGForce(accelerometer);
        
        return new EnhancedTelematicsMessage(
            driver.getPolicyId(),
            driver.getVin(),
            Instant.now(),
            driver.getCurrentSpeed(),
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
            gForce,
            sensors
        );
    }

    private EnhancedTelematicsMessage generateDrivingData(Driver driver) {
        // Enhanced GPS data for driving
        EnhancedGpsData gps = new EnhancedGpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            driver.getCurrentSpeed() * 0.44704,    // speed in m/s (convert from mph)
            driver.getCurrentBearing(),            // bearing from driver
            1.5 + random.nextDouble() * 2.0,       // GPS accuracy (better when driving)
            10 + random.nextInt(3),                // satellite count (10-12)
            50 + random.nextLong() % 100           // fix time in ms
        );

        // Normal driving accelerometer data
        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.0),
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.0),
            roundToFourDecimals(0.8 + random.nextDouble() * 0.4)
        );

        // Normal driving gyroscope readings (turning, lane changes)
        GyroscopeData gyroscope = new GyroscopeData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.5), // Gentle pitch changes
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.8), // Lane changes, turns
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.2)  // Steering movements
        );

        // Magnetometer data (compass/magnetic field)
        MagnetometerData magnetometer = new MagnetometerData(
            roundToFourDecimals(20.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(-15.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(40.0 + random.nextDouble() * 15.0),
            driver.getCurrentBearing() // Compass heading
        );

        // Device metadata
        DeviceMetadata device = new DeviceMetadata(
            80 + random.nextInt(15),               // Battery level (80-95%)
            -60 - random.nextInt(25),              // Signal strength (-60 to -85 dBm)
            "landscape",                           // Device orientation
            random.nextBoolean(),                  // Screen on/off
            random.nextBoolean()                   // Charging status
        );

        // Enhanced sensor data with all sensors
        EnhancedSensorData sensors = new EnhancedSensorData(
            gps,
            accelerometer,
            gyroscope,
            magnetometer,
            1013.0 + random.nextDouble() * 8.0,    // Barometric pressure
            device
        );

        // Calculate G-force from accelerometer data
        double gForce = calculateGForce(accelerometer);
        
        return new EnhancedTelematicsMessage(
            driver.getPolicyId(),
            driver.getVin(),
            Instant.now(),
            roundToTwoDecimals(driver.getCurrentSpeed()),
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
            gForce,
            sensors
        );
    }

    private EnhancedTelematicsMessage generateStationaryData(Driver driver) {
        // Enhanced GPS data for stationary vehicle
        EnhancedGpsData gps = new EnhancedGpsData(
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            0.0,                                   // speed is 0 when stationary
            driver.getCurrentBearing(),            // bearing from driver
            1.0 + random.nextDouble() * 1.5,       // GPS accuracy (good when stationary)
            12 + random.nextInt(2),                // satellite count (12-13, best reception)
            30 + random.nextLong() % 50            // fix time in ms (fast when stationary)
        );

        // Very low accelerometer readings for stationary vehicle
        AccelerometerData accelerometer = new AccelerometerData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.1), // Minimal vibration
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.1),
            roundToFourDecimals(0.98 + random.nextDouble() * 0.04)  // Nearly 1G downward
        );

        // Minimal gyroscope readings (no rotation when parked)
        GyroscopeData gyroscope = new GyroscopeData(
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.05), // Tiny vibrations
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.05),
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.05)
        );

        // Magnetometer data (compass/magnetic field)
        MagnetometerData magnetometer = new MagnetometerData(
            roundToFourDecimals(20.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(-15.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(40.0 + random.nextDouble() * 15.0),
            driver.getCurrentBearing() // Compass heading
        );

        // Device metadata (potentially different when parked)
        DeviceMetadata device = new DeviceMetadata(
            85 + random.nextInt(10),               // Battery level (85-95%, may be charging)
            -65 - random.nextInt(20),              // Signal strength (-65 to -85 dBm)
            random.nextBoolean() ? "portrait" : "face_up", // Device orientation when parked
            random.nextBoolean(),                  // Screen on/off
            random.nextDouble() > 0.3              // Higher chance of charging when parked
        );

        // Enhanced sensor data with all sensors
        EnhancedSensorData sensors = new EnhancedSensorData(
            gps,
            accelerometer,
            gyroscope,
            magnetometer,
            1013.2 + random.nextDouble() * 5.0,    // Stable barometric pressure
            device
        );

        // Calculate G-force from accelerometer data (minimal for stationary)
        double gForce = calculateGForce(accelerometer);
        
        return new EnhancedTelematicsMessage(
            driver.getPolicyId(),
            driver.getVin(),
            Instant.now(),
            0.0, // Zero speed when stationary
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
            gForce,
            sensors
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