package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record TelematicsMessage(
    @JsonProperty("policy_id") String policyId,
    Instant timestamp,
    @JsonProperty("speed_mph") double speedMph,
    SensorData sensors,
    @JsonProperty("g_force") double gForce
) {
}