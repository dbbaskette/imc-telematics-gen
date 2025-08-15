package com.insurancemegacorp.telematicsgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Daily routine configuration for a driver.
 * Defines home base and 4 remote locations for realistic daily patterns.
 */
public record DailyRoutine(
    @JsonProperty("driver_id") int driverId,
    @JsonProperty("driver_name") String driverName,
    @JsonProperty("base_location") Location baseLocation,
    @JsonProperty("remote_locations") List<RemoteLocation> remoteLocations,
    @JsonProperty("standard_sequence") List<String> standardSequence,
    @JsonProperty("sequence_probability") double sequenceProbability
) {
    
    /**
     * Location data for any point in the routine
     */
    public record Location(
        String name,
        double latitude,
        double longitude,
        String description
    ) {}
    
    /**
     * Remote location with unique ID for routing
     */
    public record RemoteLocation(
        String id,
        String name,
        double latitude,
        double longitude,
        String description
    ) {
        public Location toLocation() {
            return new Location(name, latitude, longitude, description);
        }
    }
    
    /**
     * Get a remote location by its ID
     */
    public RemoteLocation getLocationById(String id) {
        return remoteLocations.stream()
            .filter(loc -> loc.id().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get the full daily route sequence (including base at start and end)
     */
    public List<String> getFullSequence(List<String> remoteSequence) {
        java.util.ArrayList<String> fullSequence = new java.util.ArrayList<>();
        fullSequence.add("BASE");
        fullSequence.addAll(remoteSequence);
        fullSequence.add("BASE");
        return fullSequence;
    }
}
