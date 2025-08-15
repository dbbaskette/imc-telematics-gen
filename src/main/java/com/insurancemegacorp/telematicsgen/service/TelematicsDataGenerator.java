package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TelematicsDataGenerator {

    private final SecureRandom random = new SecureRandom();
    @org.springframework.beans.factory.annotation.Value("${telematics.simulation.min-crash-gforce:6.0}")
    private double minCrashGForce;

    public FlatTelematicsMessage generateTelematicsData(Driver driver) {
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

    public FlatTelematicsMessage generateCrashEventData(Driver driver) {
        // Generate crash-level accelerometer readings
        double accelX = roundToFourDecimals(5.0 + random.nextDouble() * 3.0);
        double accelY = roundToFourDecimals(4.0 + random.nextDouble() * 3.0);
        double accelZ = roundToFourDecimals((random.nextDouble() - 0.5) * 4.0);
        
        // Calculate G-force and enforce minimum threshold for crash
        double gForce = Math.max(minCrashGForce, Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ));
        
        return new FlatTelematicsMessage(
            // Core message fields
            driver.getPolicyId(),
            driver.getVehicleId(),
            driver.getVin(),
            Instant.now(),
            0.0, // Speed is zero during crash event
            driver.getSpeedLimit(),
            gForce,
            driver.getDriverId(),
            driver.getCurrentStreet(),
            
            // GPS data fields
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            0.0,                                   // GPS speed (zero during crash)
            driver.getCurrentBearing(),            // bearing from driver
            2.0 + random.nextDouble() * 3.0,       // GPS accuracy
            8 + random.nextInt(4),                 // satellite count (8-11)
            100 + random.nextLong() % 200,         // fix time in ms
            
            // Accelerometer data fields
            accelX,
            accelY,
            accelZ,
            
            // Gyroscope data fields (extreme readings during crash)
            roundToFourDecimals((random.nextDouble() - 0.5) * 6.0), // High pitch rotation
            roundToFourDecimals((random.nextDouble() - 0.5) * 8.0), // High roll rotation  
            roundToFourDecimals((random.nextDouble() - 0.5) * 4.0), // High yaw rotation
            
            // Magnetometer data fields
            roundToFourDecimals(20.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(-15.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(40.0 + random.nextDouble() * 15.0),
            driver.getCurrentBearing(), // Compass heading
            
            // Environmental data
            1010.0 + random.nextDouble() * 10.0, // Barometric pressure
            
            // Device metadata fields
            75 + random.nextInt(20),               // Battery level (75-95%)
            -70 - random.nextInt(30),              // Signal strength (-70 to -100 dBm)
            "landscape",                           // Device orientation
            random.nextBoolean(),                  // Screen on/off
            random.nextBoolean()                   // Charging status
        );
    }

    private FlatTelematicsMessage generateDrivingData(Driver driver) {
        // Normal driving accelerometer data
        double accelX = roundToFourDecimals((random.nextDouble() - 0.5) * 1.0);
        double accelY = roundToFourDecimals((random.nextDouble() - 0.5) * 1.0);
        double accelZ = roundToFourDecimals(0.8 + random.nextDouble() * 0.4);
        
        // Calculate G-force from accelerometer data
        double gForce = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        
        return new FlatTelematicsMessage(
            // Core message fields
            driver.getPolicyId(),
            driver.getVehicleId(),
            driver.getVin(),
            Instant.now(),
            roundToTwoDecimals(driver.getCurrentSpeed()),
            driver.getSpeedLimit(),
            gForce,
            driver.getDriverId(),
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
            
            // GPS data fields
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            driver.getCurrentSpeed() * 0.44704,    // speed in m/s (convert from mph)
            driver.getCurrentBearing(),            // bearing from driver
            1.5 + random.nextDouble() * 2.0,       // GPS accuracy (better when driving)
            10 + random.nextInt(3),                // satellite count (10-12)
            50 + random.nextLong() % 100,          // fix time in ms
            
            // Accelerometer data fields
            accelX,
            accelY,
            accelZ,
            
            // Gyroscope data fields (normal driving - turning, lane changes)
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.5), // Gentle pitch changes
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.8), // Lane changes, turns
            roundToFourDecimals((random.nextDouble() - 0.5) * 1.2), // Steering movements
            
            // Magnetometer data fields (compass/magnetic field)
            roundToFourDecimals(20.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(-15.0 + random.nextDouble() * 10.0),
            roundToFourDecimals(40.0 + random.nextDouble() * 15.0),
            driver.getCurrentBearing(), // Compass heading
            
            // Environmental data
            1013.0 + random.nextDouble() * 8.0,    // Barometric pressure
            
            // Device metadata fields
            80 + random.nextInt(15),               // Battery level (80-95%)
            -60 - random.nextInt(25),              // Signal strength (-60 to -85 dBm)
            "landscape",                           // Device orientation
            random.nextBoolean(),                  // Screen on/off
            random.nextBoolean()                   // Charging status
        );
    }

    private FlatTelematicsMessage generateStationaryData(Driver driver) {
        // Minimal accelerometer data for stationary vehicle
        double accelX = roundToFourDecimals((random.nextDouble() - 0.5) * 0.1);
        double accelY = roundToFourDecimals((random.nextDouble() - 0.5) * 0.1);
        double accelZ = roundToFourDecimals(0.95 + random.nextDouble() * 0.1);
        
        // Calculate G-force from accelerometer data (minimal for stationary)
        double gForce = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        
        return new FlatTelematicsMessage(
            // Core message fields
            driver.getPolicyId(),
            driver.getVehicleId(),
            driver.getVin(),
            Instant.now(),
            0.0, // Zero speed when stationary
            driver.getSpeedLimit(),
            gForce,
            driver.getDriverId(),
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
            
            // GPS data fields
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            0.0,                                   // GPS speed (zero when stationary)
            driver.getCurrentBearing(),            // bearing from driver
            0.8 + random.nextDouble() * 1.0,       // GPS accuracy (excellent when stationary)
            11 + random.nextInt(2),                // satellite count (11-12)
            30 + random.nextLong() % 50,           // fix time in ms (fast when stationary)
            
            // Accelerometer data fields
            accelX,
            accelY,
            accelZ,
            
            // Gyroscope data fields (minimal movement when stationary)
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.02), // Tiny pitch variations
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.02), // Tiny roll variations
            roundToFourDecimals((random.nextDouble() - 0.5) * 0.02), // Tiny yaw variations
            
            // Magnetometer data fields (stable when stationary)
            roundToFourDecimals(22.0 + random.nextDouble() * 6.0),
            roundToFourDecimals(-12.0 + random.nextDouble() * 6.0),
            roundToFourDecimals(42.0 + random.nextDouble() * 8.0),
            driver.getCurrentBearing(), // Compass heading
            
            // Environmental data
            1013.2 + random.nextDouble() * 5.0,    // Stable barometric pressure
            
            // Device metadata fields (potentially different when parked)
            85 + random.nextInt(10),               // Battery level (85-95%, may be charging)
            -65 - random.nextInt(20),              // Signal strength (-65 to -85 dBm)
            random.nextBoolean() ? "portrait" : "face_up", // Device orientation when parked
            random.nextBoolean(),                  // Screen on/off
            random.nextDouble() > 0.3              // Higher chance of charging when parked
        );
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}