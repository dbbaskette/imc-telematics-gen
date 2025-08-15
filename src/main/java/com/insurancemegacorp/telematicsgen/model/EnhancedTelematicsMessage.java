package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record EnhancedTelematicsMessage(
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("event_time") Instant eventTime,
    @JsonProperty("is_crash_event") boolean isCrashEvent,
    EnhancedGpsData gps,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("speed_limit_mph") int speedLimitMph,
    EnhancedSensorData sensors,
    @JsonProperty("g_force") double gForce,
    @JsonProperty("driver_id") String driverId,
    @JsonProperty("current_street") String currentStreet
) {
}