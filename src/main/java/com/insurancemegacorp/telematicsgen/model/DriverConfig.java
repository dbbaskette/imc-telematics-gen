package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration model for individual drivers loaded from file-based configuration.
 * Contains all the essential information needed to initialize and track a driver.
 */
public record DriverConfig(
    @JsonProperty("driver_id") int driverId,
    @JsonProperty("policy_id") int policyId,
    @JsonProperty("vin") String vin,
    @JsonProperty("vehicle_id") int vehicleId,
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
     * Get the raw numeric driver ID for consistency with other ID fields.
     * Returns the driver ID as an integer.
     */
    public int getDriverId() {
        return driverId;
    }
    
    /**
     * Get a display name combining driver number and name for UI purposes.
     */
    public String getDisplayName() {
        return String.format("#%d - %s", driverId, driverName);
    }
    
    /**
     * Get vehicle description for display purposes.
     */
    public String getVehicleDescription() {
        return String.format("%d %s %s", vehicleYear, vehicleMake, vehicleModel);
    }
}
