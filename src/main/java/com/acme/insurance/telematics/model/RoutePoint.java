package com.acme.insurance.telematics.model;

public record RoutePoint(
    double latitude,
    double longitude,
    String streetName,
    int speedLimitMph,
    boolean isIntersection,
    String intersectionType  // "traffic_light", "stop_sign", "yield", "none"
) {
}