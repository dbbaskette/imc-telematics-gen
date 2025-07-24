package com.acme.insurance.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EnhancedGpsData(
    @JsonProperty("lat") double latitude,
    @JsonProperty("lon") double longitude,
    double altitude,              // Meters above sea level
    double speed,                 // Speed from GPS (m/s)
    double bearing,               // Direction of travel (degrees, 0=North)
    double accuracy,              // GPS accuracy in meters
    @JsonProperty("satellite_count") int satelliteCount,
    @JsonProperty("fix_time") long fixTime  // Time to get GPS fix (ms)
) {
}