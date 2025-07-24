package com.acme.insurance.telematics.model;

public record GyroscopeData(
    double x, // Angular velocity around x-axis (pitch) - rad/s
    double y, // Angular velocity around y-axis (roll) - rad/s  
    double z  // Angular velocity around z-axis (yaw/turning) - rad/s
) {
}