package com.acme.insurance.telematics.model;

public record MagnetometerData(
    double x,       // Magnetic field strength in x-axis (μT)
    double y,       // Magnetic field strength in y-axis (μT)
    double z,       // Magnetic field strength in z-axis (μT)
    double heading  // Compass heading in degrees (0-360, 0=North)
) {
}