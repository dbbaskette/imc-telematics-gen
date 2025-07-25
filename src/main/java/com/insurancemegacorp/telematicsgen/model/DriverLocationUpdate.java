package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record DriverLocationUpdate(
    @JsonProperty("driver_id") String driverId,
    @JsonProperty("policy_id") String policyId,
    double latitude,
    double longitude,
    double bearing,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("current_street") String currentStreet,
    DriverState state,
    @JsonProperty("route_description") String routeDescription,
    @JsonProperty("is_crash_event") boolean isCrashEvent,
    @JsonProperty("g_force") double gForce,
    Instant timestamp
) {
}