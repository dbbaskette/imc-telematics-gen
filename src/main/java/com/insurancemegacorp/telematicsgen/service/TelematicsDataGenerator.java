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
            case POST_CRASH_IDLE -> {
                // Post-crash data should be frozen/static until state changes
                return generatePostCrashData(driver);
            }
            case PARKED, TRAFFIC_STOP, BREAK_TIME -> {
                return generateStationaryData(driver);
            }
            default -> {
                return generateDrivingData(driver);
            }
        }
    }

    /**
     * Generate crash event data with a weighted random accident type.
     * Weights are based on real-world accident frequency statistics.
     */
    public FlatTelematicsMessage generateCrashEventData(Driver driver) {
        AccidentType accidentType = selectWeightedAccidentType();
        return generateCrashEventData(driver, accidentType);
    }

    /**
     * Select an accident type using weighted probabilities based on real-world statistics.
     * For MOVING drivers only - stopped drivers always get REAR_ENDED.
     * Note: REAR_ENDED (being hit from behind) is rare when actively driving,
     * so it's excluded here - use the specific method for stopped vehicles.
     */
    private AccidentType selectWeightedAccidentType() {
        // Weights for MOVING vehicles (total = 100)
        // REAR_ENDED excluded - that's for stopped vehicles
        double roll = random.nextDouble() * 100;

        if (roll < 35) {
            // 35% - Rear-end collision (hitting someone ahead)
            return AccidentType.REAR_END_COLLISION;
        } else if (roll < 55) {
            // 20% - Side-swipes common on highways and lane changes
            return AccidentType.SIDE_SWIPE;
        } else if (roll < 73) {
            // 18% - T-bone at intersections
            return AccidentType.T_BONE;
        } else if (roll < 88) {
            // 15% - Single vehicle (poles, barriers, run off road)
            return AccidentType.SINGLE_VEHICLE;
        } else if (roll < 94) {
            // 6% - Hit and run
            return AccidentType.HIT_AND_RUN;
        } else if (roll < 97) {
            // 3% - Multi-vehicle pileup (rare)
            return AccidentType.MULTI_VEHICLE_PILEUP;
        } else if (roll < 99) {
            // 2% - Head-on (rare but severe)
            return AccidentType.HEAD_ON;
        } else {
            // 1% - Rollover (rare)
            return AccidentType.ROLLOVER;
        }
    }

    /**
     * Generate crash event data with a specific accident type.
     * Each accident type has characteristic sensor signatures.
     * Speed at impact is captured from the driver's current speed before the crash.
     */
    public FlatTelematicsMessage generateCrashEventData(Driver driver, AccidentType accidentType) {
        AccidentType.SensorProfile profile = accidentType.getSensorProfile();

        // Use stored speed at impact (captured before speed was set to 0)
        Double storedSpeed = driver.getCrashSpeedAtImpact();
        double speedAtImpact = (storedSpeed != null) ? storedSpeed : driver.getCurrentSpeed();

        // Generate accelerometer readings based on accident type profile
        double accelX = roundToFourDecimals(profile.generateAccelX(random));
        double accelY = roundToFourDecimals(profile.generateAccelY(random));
        double accelZ = roundToFourDecimals(profile.generateAccelZ(random));

        // Generate gyroscope readings based on accident type profile
        double gyroX = roundToFourDecimals(profile.generateGyroX(random));
        double gyroY = roundToFourDecimals(profile.generateGyroY(random));
        double gyroZ = roundToFourDecimals(profile.generateGyroZ(random));

        // Calculate G-force and enforce minimum threshold for crash
        double gForce = Math.max(minCrashGForce, Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ));

        return new FlatTelematicsMessage(
            // Core message fields
            driver.getPolicyId(),
            driver.getVehicleId(),
            driver.getVin(),
            Instant.now(),
            roundToTwoDecimals(speedAtImpact), // Speed at moment of impact
            driver.getSpeedLimit(),
            gForce,
            driver.getDriverId(),
            driver.getCurrentStreet(),
            accidentType.name(), // Accident type as string

            // GPS data fields
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.5 + random.nextDouble() * 50.0,    // altitude
            speedAtImpact * 0.44704,               // GPS speed in m/s at impact
            driver.getCurrentBearing(),            // bearing from driver
            2.0 + random.nextDouble() * 3.0,       // GPS accuracy
            8 + random.nextInt(4),                 // satellite count (8-11)
            100 + random.nextLong() % 200,         // fix time in ms

            // Accelerometer data fields - from accident type profile
            accelX,
            accelY,
            accelZ,

            // Gyroscope data fields - from accident type profile
            gyroX,
            gyroY,
            gyroZ,

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
            null, // No accident type for normal driving

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

    /**
     * Generate frozen/static data for post-crash state.
     * Values remain constant to indicate the vehicle is immobile after an accident.
     */
    private FlatTelematicsMessage generatePostCrashData(Driver driver) {
        // Use crash snapshot data if available (accident type persists during post-crash)
        String accidentType = driver.getCrashAccidentType();

        return new FlatTelematicsMessage(
            // Core message fields
            driver.getPolicyId(),
            driver.getVehicleId(),
            driver.getVin(),
            Instant.now(),
            0.0, // Vehicle is now stationary after crash
            driver.getSpeedLimit(),
            1.0, // Normal G-force (vehicle at rest)
            driver.getDriverId(),
            driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Crash Site",
            accidentType, // Keep showing accident type during post-crash

            // GPS data fields - frozen at crash location
            driver.getCurrentLatitude(),
            driver.getCurrentLongitude(),
            320.0,  // Fixed altitude
            0.0,    // Zero speed
            driver.getCurrentBearing(),
            1.5,    // Fixed GPS accuracy
            12,     // Fixed satellite count
            40L,    // Fixed fix time

            // Accelerometer data - minimal, vehicle at rest
            0.0,
            0.0,
            1.0,  // Only gravity

            // Gyroscope data - zero rotation
            0.0,
            0.0,
            0.0,

            // Magnetometer data - stable
            25.0,
            -10.0,
            45.0,
            driver.getCurrentBearing(),

            // Environmental data
            1013.0,

            // Device metadata - frozen values
            85,      // Battery level
            -70,     // Signal strength
            "face_up",  // Device likely thrown/displaced
            true,    // Screen on (emergency)
            false    // Not charging
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
            null, // No accident type for stationary

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