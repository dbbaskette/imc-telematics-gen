package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GpsData(
    @JsonProperty("lat") double latitude,
    @JsonProperty("lon") double longitude
) {
}