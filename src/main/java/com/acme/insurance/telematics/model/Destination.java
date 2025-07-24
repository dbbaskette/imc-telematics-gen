package com.acme.insurance.telematics.model;

/**
 * Represents a destination for a driver's trip
 */
public record Destination(
    double latitude,
    double longitude,
    String name,
    String type,  // "restaurant", "shopping", "work", "home", "gas_station", "hospital", etc.
    double distanceFromOriginMiles
) {
    
    /**
     * Calculate the distance between two geographic points using the Haversine formula
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 3959; // Earth's radius in miles
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
    
    /**
     * Calculate bearing from one point to another
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360; // Normalize to 0-360
    }
}
