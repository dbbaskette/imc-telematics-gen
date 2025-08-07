package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record EnhancedTelematicsMessage(
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("vin") String vin,
    Instant timestamp,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("current_street") String currentStreet,
    @JsonProperty("g_force") double gForce,
    EnhancedSensorData sensors
) {
}