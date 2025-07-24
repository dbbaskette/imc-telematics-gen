package com.acme.insurance.telematics.model;

public record EnhancedSensorData(
    EnhancedGpsData gps,
    AccelerometerData accelerometer,
    GyroscopeData gyroscope,
    MagnetometerData magnetometer,
    double barometricPressure,  // hPa (hectopascals)
    DeviceMetadata device
) {
}