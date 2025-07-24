package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceMetadata(
    @JsonProperty("battery_level") int batteryLevel,     // Battery percentage (0-100)
    @JsonProperty("signal_strength") int signalStrength, // Cellular signal (-120 to -30 dBm)
    @JsonProperty("device_orientation") String orientation, // "portrait", "landscape", "face_up", etc.
    @JsonProperty("screen_on") boolean screenOn,         // Is screen currently on
    @JsonProperty("charging") boolean charging           // Is device charging
) {
}