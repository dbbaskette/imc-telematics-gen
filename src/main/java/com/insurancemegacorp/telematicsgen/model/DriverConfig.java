package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration model for individual drivers loaded from file-based configuration.
 * Contains all the essential information needed to initialize and track a driver.
 */
public record DriverConfig(
    @JsonProperty("driver_number") int driverNumber,
    @JsonProperty("policy_number") String policyNumber,
    @JsonProperty("vin") String vin,
    @JsonProperty("driver_name") String driverName,
    @JsonProperty("vehicle_make") String vehicleMake,
    @JsonProperty("vehicle_model") String vehicleModel,
    @JsonProperty("vehicle_year") int vehicleYear,
    @JsonProperty("license_plate") String licensePlate,
    @JsonProperty("base_latitude") double baseLatitude,
    @JsonProperty("base_longitude") double baseLongitude,
    @JsonProperty("preferred_route") String preferredRoute
) {
    
    /**
     * Generate a driver ID for internal use based on the driver number.
     * Format: DRIVER-001, DRIVER-002, etc.
     */
    public String getDriverId() {
        return String.format("DRIVER-%03d", driverNumber);
    }
    
    /**
     * Get a display name combining driver number and name for UI purposes.
     */
    public String getDisplayName() {
        return String.format("#%d - %s", driverNumber, driverName);
    }
    
    /**
     * Get vehicle description for display purposes.
     */
    public String getVehicleDescription() {
        return String.format("%d %s %s", vehicleYear, vehicleMake, vehicleModel);
    }
}
