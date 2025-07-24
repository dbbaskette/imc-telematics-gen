package com.insurancemegacorp.telematicsgen.model;

public record SensorData(
    GpsData gps,
    AccelerometerData accelerometer
) {
}