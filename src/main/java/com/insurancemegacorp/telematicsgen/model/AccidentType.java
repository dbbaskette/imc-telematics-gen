package com.insurancemegacorp.telematicsgen.model;

/**
 * Types of vehicle accidents with characteristic sensor signatures.
 * Each type has distinct accelerometer and gyroscope patterns.
 */
public enum AccidentType {

    /** Vehicle is struck from behind by another vehicle */
    REAR_ENDED("Rear-ended", "Vehicle was struck from behind",
        new SensorProfile(2.0, 6.0, -1.0, 2.0, -1.0, 1.0,   // Accel: strong +X (forward jolt)
                         -2.0, 2.0, -1.0, 1.0, -1.0, 1.0)), // Gyro: mild pitch forward

    /** Vehicle strikes another vehicle from behind */
    REAR_END_COLLISION("Rear-end collision", "Vehicle struck another from behind",
        new SensorProfile(-4.0, -8.0, -1.0, 2.0, -1.0, 1.0,  // Accel: strong -X (deceleration)
                         2.0, 4.0, -1.0, 1.0, -1.0, 1.0)),   // Gyro: pitch forward from impact

    /** Vehicle is struck on the side, perpendicular impact */
    T_BONE("T-bone collision", "Vehicle struck on side by perpendicular vehicle",
        new SensorProfile(-1.0, 2.0, 5.0, 9.0, -1.0, 1.0,    // Accel: strong lateral Y
                         -1.0, 1.0, 3.0, 6.0, 2.0, 4.0)),    // Gyro: high roll, yaw from spin

    /** Vehicle strikes or is struck alongside while moving same direction */
    SIDE_SWIPE("Side-swipe", "Glancing side impact while traveling",
        new SensorProfile(-1.0, 2.0, 2.0, 4.0, -0.5, 0.5,    // Accel: moderate lateral
                         -0.5, 0.5, 1.0, 2.0, 1.0, 3.0)),    // Gyro: mild roll, yaw deviation

    /** Head-on collision with oncoming vehicle */
    HEAD_ON("Head-on collision", "Frontal collision with oncoming vehicle",
        new SensorProfile(-8.0, -12.0, -2.0, 2.0, -1.0, 2.0, // Accel: extreme -X deceleration
                         3.0, 6.0, -2.0, 2.0, -1.0, 1.0)),   // Gyro: strong pitch

    /** Vehicle rolls over, one or more times */
    ROLLOVER("Rollover", "Vehicle rolled over during accident",
        new SensorProfile(-2.0, 4.0, -3.0, 6.0, -8.0, 8.0,   // Accel: chaotic all axes, high Z
                         4.0, 10.0, 6.0, 12.0, 3.0, 8.0)),   // Gyro: extreme rotation all axes

    /** Single vehicle collision with fixed object */
    SINGLE_VEHICLE("Single vehicle collision", "Collision with pole, barrier, or fixed object",
        new SensorProfile(-5.0, -9.0, -2.0, 3.0, -1.0, 2.0,  // Accel: strong frontal impact
                         2.0, 5.0, -1.0, 2.0, -1.0, 2.0)),   // Gyro: moderate pitch

    /** Multi-vehicle pileup accident */
    MULTI_VEHICLE_PILEUP("Multi-vehicle pileup", "Involved in chain-reaction collision",
        new SensorProfile(-4.0, -7.0, -3.0, 5.0, -2.0, 2.0,  // Accel: multiple impacts
                         2.0, 4.0, 2.0, 4.0, 2.0, 4.0)),     // Gyro: varied rotation

    /** Vehicle was struck by another that fled the scene */
    HIT_AND_RUN("Hit and run", "Struck by vehicle that fled scene",
        new SensorProfile(-2.0, 5.0, 2.0, 6.0, -1.0, 1.0,    // Accel: varies by impact angle
                         -1.0, 2.0, 1.0, 3.0, 1.0, 3.0));    // Gyro: moderate all axes

    private final String displayName;
    private final String description;
    private final SensorProfile sensorProfile;

    AccidentType(String displayName, String description, SensorProfile sensorProfile) {
        this.displayName = displayName;
        this.description = description;
        this.sensorProfile = sensorProfile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public SensorProfile getSensorProfile() {
        return sensorProfile;
    }

    /**
     * Sensor profile defining characteristic ranges for accelerometer and gyroscope
     * readings for this accident type.
     */
    public record SensorProfile(
        double accelXMin, double accelXMax,
        double accelYMin, double accelYMax,
        double accelZMin, double accelZMax,
        double gyroXMin, double gyroXMax,
        double gyroYMin, double gyroYMax,
        double gyroZMin, double gyroZMax
    ) {
        /** Generate accelerometer X value within this profile's range */
        public double generateAccelX(java.util.Random random) {
            return accelXMin + random.nextDouble() * (accelXMax - accelXMin);
        }

        /** Generate accelerometer Y value within this profile's range */
        public double generateAccelY(java.util.Random random) {
            return accelYMin + random.nextDouble() * (accelYMax - accelYMin);
        }

        /** Generate accelerometer Z value within this profile's range */
        public double generateAccelZ(java.util.Random random) {
            return accelZMin + random.nextDouble() * (accelZMax - accelZMin);
        }

        /** Generate gyroscope X (pitch) value within this profile's range */
        public double generateGyroX(java.util.Random random) {
            return gyroXMin + random.nextDouble() * (gyroXMax - gyroXMin);
        }

        /** Generate gyroscope Y (roll) value within this profile's range */
        public double generateGyroY(java.util.Random random) {
            return gyroYMin + random.nextDouble() * (gyroYMax - gyroYMin);
        }

        /** Generate gyroscope Z (yaw) value within this profile's range */
        public double generateGyroZ(java.util.Random random) {
            return gyroZMin + random.nextDouble() * (gyroZMax - gyroZMin);
        }
    }
}
