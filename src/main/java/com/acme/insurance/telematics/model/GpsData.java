package com.acme.insurance.telematics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GpsData(
    @JsonProperty("lat") double latitude,
    @JsonProperty("lon") double longitude
) {
}