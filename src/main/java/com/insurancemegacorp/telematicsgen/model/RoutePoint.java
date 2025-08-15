package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoutePoint(
    double latitude,
    double longitude,
    @JsonProperty("street_name") String streetName,
    @JsonProperty("speed_limit") int speedLimit,
    @JsonProperty("has_traffic_light") boolean hasTrafficLight,
    @JsonProperty("traffic_control") String trafficControl
) {
}