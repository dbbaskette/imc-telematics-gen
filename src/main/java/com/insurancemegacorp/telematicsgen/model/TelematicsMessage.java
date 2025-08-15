package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelematicsMessage(
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vehicle_id") int vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("event_time") Instant eventTime,
    GpsData gps,
    @JsonProperty("speed_mph") double speedMph,
    @JsonProperty("speed_limit_mph") int speedLimitMph,
    SensorData sensors,
    @JsonProperty("g_force") double gForce
) {
}