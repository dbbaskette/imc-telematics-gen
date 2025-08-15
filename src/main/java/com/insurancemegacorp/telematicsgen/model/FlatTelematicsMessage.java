package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Flattened version of EnhancedTelematicsMessage for simplified downstream processing.
 * All nested sensor data is flattened using prefix naming convention (e.g., gps_latitude, device_battery_level).
 * This structure is optimized for RabbitMQ publishing and direct SQL mapping.
 */
public record FlatTelematicsMessage(
    // Core message fields
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("event_time") Instant eventTime,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("speed_limit_mph") int speedLimitMph,
    @JsonProperty("g_force") double gForce,
    @JsonProperty("driver_id") int driverId,
    @JsonProperty("current_street") String currentStreet,
    
    // GPS data fields (from sensors.gps)
    @JsonProperty("gps_latitude") double gpsLatitude,
    @JsonProperty("gps_longitude") double gpsLongitude,
    @JsonProperty("gps_altitude") double gpsAltitude,
    @JsonProperty("gps_speed") double gpsSpeed,
    @JsonProperty("gps_bearing") double gpsBearing,
    @JsonProperty("gps_accuracy") double gpsAccuracy,
    @JsonProperty("gps_satellite_count") int gpsSatelliteCount,
    @JsonProperty("gps_fix_time") long gpsFixTime,
    
    // Accelerometer data fields (from sensors.accelerometer)
    @JsonProperty("accelerometer_x") double accelerometerX,
    @JsonProperty("accelerometer_y") double accelerometerY,
    @JsonProperty("accelerometer_z") double accelerometerZ,
    
    // Gyroscope data fields (from sensors.gyroscope)
    @JsonProperty("gyroscope_x") double gyroscopeX,
    @JsonProperty("gyroscope_y") double gyroscopeY,
    @JsonProperty("gyroscope_z") double gyroscopeZ,
    
    // Magnetometer data fields (from sensors.magnetometer)
    @JsonProperty("magnetometer_x") double magnetometerX,
    @JsonProperty("magnetometer_y") double magnetometerY,
    @JsonProperty("magnetometer_z") double magnetometerZ,
    @JsonProperty("magnetometer_heading") double magnetometerHeading,
    
    // Environmental data (from sensors.barometricPressure)
    @JsonProperty("barometric_pressure") double barometricPressure,
    
    // Device metadata fields (from sensors.device)
    @JsonProperty("device_battery_level") int deviceBatteryLevel,
    @JsonProperty("device_signal_strength") int deviceSignalStrength,
    @JsonProperty("device_orientation") String deviceOrientation,
    @JsonProperty("device_screen_on") boolean deviceScreenOn,
    @JsonProperty("device_charging") boolean deviceCharging
) {
    

}
