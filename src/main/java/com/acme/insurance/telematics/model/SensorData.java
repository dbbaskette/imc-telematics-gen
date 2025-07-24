package com.acme.insurance.telematics.model;

public record SensorData(
    GpsData gps,
    AccelerometerData accelerometer
) {
}