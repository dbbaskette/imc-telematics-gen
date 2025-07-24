package com.acme.insurance.telematics.model;

public enum DriverState {
    DRIVING,           // Normal driving behavior
    PARKED,           // Vehicle is parked/stationary
    POST_CRASH_IDLE,  // Sitting still after a crash event
    TRAFFIC_STOP,     // Temporarily stopped (traffic light, etc.)
    BREAK_TIME        // Driver taking a break
}