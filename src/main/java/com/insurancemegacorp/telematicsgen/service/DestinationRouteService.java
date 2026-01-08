package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Destination;
import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced routing service that generates dynamic routes to random destinations
 * within a configurable radius around Atlanta
 */
@Service
public class DestinationRouteService {

    private static final Logger logger = LoggerFactory.getLogger(DestinationRouteService.class);
    private final SecureRandom random = new SecureRandom();
    
    // Atlanta center coordinates
    private static final double ATLANTA_CENTER_LAT = 33.7490;
    private static final double ATLANTA_CENTER_LON = -84.3880;
    
    // Circular boundary for destinations (80-mile radius)
    private static final double MAX_RADIUS_MILES = 80.0;
    private static final double MIN_TRIP_DISTANCE_MILES = 5.0;
    
    // Destination types with realistic Atlanta area names
    private static final String[] DESTINATION_TYPES = {
        "restaurant", "shopping", "work", "gas_station", "hospital", 
        "school", "park", "airport", "hotel", "entertainment"
    };
    
    private static final String[] DESTINATION_NAMES = {
        "Lenox Square Mall", "Piedmont Park", "Georgia Tech", "Emory University",
        "Hartsfield-Jackson Airport", "CNN Center", "World of Coca-Cola", 
        "Fox Theatre", "Mercedes-Benz Stadium", "State Farm Arena",
        "Buckhead Village", "Little Five Points", "Virginia-Highland",
        "Decatur Square", "Marietta Square", "Stone Mountain Park",
        "Six Flags Over Georgia", "Zoo Atlanta", "Atlanta Botanical Garden",
        "High Museum of Art", "Centennial Olympic Park", "Underground Atlanta",
        "Atlantic Station", "Ponce City Market", "Krog Street Market",
        "Westside Provisions District", "The Battery Atlanta", "Town Center at Cobb",
        "Perimeter Mall", "North Point Mall", "Southlake Mall"
    };

    /**
     * Generate a random destination within the circular boundary around Atlanta
     */
    public Destination generateRandomDestination() {
        // Generate random distance between min and max radius
        double distance = MIN_TRIP_DISTANCE_MILES + 
                         (random.nextDouble() * (MAX_RADIUS_MILES - MIN_TRIP_DISTANCE_MILES));
        
        // Generate random bearing (0-360 degrees)
        double bearing = random.nextDouble() * 360.0;
        
        // Convert to coordinates using the existing helper method
        double[] coords = calculateDestinationCoordinates(
            ATLANTA_CENTER_LAT, ATLANTA_CENTER_LON, distance, bearing);
        
        // Select random destination type and name
        String type = DESTINATION_TYPES[random.nextInt(DESTINATION_TYPES.length)];
        String name = DESTINATION_NAMES[random.nextInt(DESTINATION_NAMES.length)];
        
        return new Destination(coords[0], coords[1], name, type, distance);
    }

    /**
     * Generate a realistic route from current location to destination
     * with multiple waypoints for smooth movement
     */
    public List<RoutePoint> generateRouteToDestination(
            double startLat, double startLon, Destination destination) {
        
        List<RoutePoint> route = new ArrayList<>();
        
        // Calculate total distance and bearing
        double totalDistance = Destination.calculateDistance(
            startLat, startLon, destination.latitude(), destination.longitude());
        double mainBearing = Destination.calculateBearing(
            startLat, startLon, destination.latitude(), destination.longitude());
        
        // Determine number of waypoints based on distance (more waypoints = smoother movement)
        int numWaypoints = Math.max(10, (int) (totalDistance * 3)); // ~3 waypoints per mile
        
        logger.info("🗺️  Generating route with {} waypoints over {:.1f} miles to {}", 
                   numWaypoints, totalDistance, destination.name());
        
        // Generate waypoints with slight variations for realistic routing
        for (int i = 0; i <= numWaypoints; i++) {
            double progress = (double) i / numWaypoints;
            
            // Add some realistic route variation (not perfectly straight)
            double bearingVariation = (random.nextGaussian() * 15.0); // ±15 degrees variation
            double currentBearing = mainBearing + bearingVariation;
            
            // Calculate intermediate distance
            double segmentDistance = totalDistance * progress;
            
            // Calculate waypoint coordinates
            double[] coords = calculateDestinationCoordinates(
                startLat, startLon, segmentDistance, currentBearing);
            
            // Determine speed limit based on distance from city center
            int speedLimit = determineSpeedLimit(coords[0], coords[1], totalDistance, progress);
            
            // Determine if this is an intersection (more likely in urban areas)
            boolean isIntersection = shouldBeIntersection(coords[0], coords[1], i, numWaypoints);
            String intersectionType = isIntersection ? selectIntersectionType() : "none";
            
            // Generate street name
            String streetName = generateStreetName(i, numWaypoints, destination);
            
            RoutePoint waypoint = new RoutePoint(
                coords[0], coords[1], streetName, speedLimit, isIntersection, intersectionType);
            
            route.add(waypoint);
        }
        
        // Ensure the final waypoint is exactly at the destination
        RoutePoint finalPoint = new RoutePoint(
            destination.latitude(), destination.longitude(), 
            destination.name(), 25, true, "destination");
        route.set(route.size() - 1, finalPoint);
        
        logger.info("✅ Generated route to {} with {} waypoints", 
                   destination.name(), route.size());
        
        return route;
    }

    /**
     * Calculate destination coordinates given start point, distance, and bearing
     */
    private double[] calculateDestinationCoordinates(double startLat, double startLon, 
                                                   double distanceMiles, double bearingDegrees) {
        final double R = 3959; // Earth's radius in miles
        double bearingRad = Math.toRadians(bearingDegrees);
        double startLatRad = Math.toRadians(startLat);
        double startLonRad = Math.toRadians(startLon);
        
        double destLatRad = Math.asin(
            Math.sin(startLatRad) * Math.cos(distanceMiles / R) +
            Math.cos(startLatRad) * Math.sin(distanceMiles / R) * Math.cos(bearingRad));
        
        double destLonRad = startLonRad + Math.atan2(
            Math.sin(bearingRad) * Math.sin(distanceMiles / R) * Math.cos(startLatRad),
            Math.cos(distanceMiles / R) - Math.sin(startLatRad) * Math.sin(destLatRad));
        
        return new double[]{Math.toDegrees(destLatRad), Math.toDegrees(destLonRad)};
    }

    /**
     * Determine appropriate speed limit based on location and route characteristics.
     * Uses realistic US speed limits: 25, 30, 35, 45, 55, 65, 70 mph.
     */
    private int determineSpeedLimit(double lat, double lon, double totalDistance, double progress) {
        // Distance from Atlanta center (in miles)
        double distanceFromCenter = Destination.calculateDistance(
            ATLANTA_CENTER_LAT, ATLANTA_CENTER_LON, lat, lon);

        // Realistic speed limits based on road type
        int[] urbanLimits = {25, 30, 35};        // Downtown streets
        int[] suburbanLimits = {35, 45};          // Suburban roads
        int[] highwayLimits = {55, 65, 70};       // Highways/interstates

        if (distanceFromCenter < 5) {
            // Downtown/urban area - 25, 30, or 35 mph
            return urbanLimits[random.nextInt(urbanLimits.length)];
        } else if (distanceFromCenter < 15) {
            // Suburban area - 35 or 45 mph
            return suburbanLimits[random.nextInt(suburbanLimits.length)];
        } else {
            // Highway/rural area - 55, 65, or 70 mph
            return highwayLimits[random.nextInt(highwayLimits.length)];
        }
    }

    /**
     * Determine if a waypoint should be an intersection
     */
    private boolean shouldBeIntersection(double lat, double lon, int waypointIndex, int totalWaypoints) {
        // More intersections in urban areas
        double distanceFromCenter = Destination.calculateDistance(
            ATLANTA_CENTER_LAT, ATLANTA_CENTER_LON, lat, lon);
        
        double intersectionProbability;
        if (distanceFromCenter < 5) {
            intersectionProbability = 0.4; // 40% chance in urban areas
        } else if (distanceFromCenter < 15) {
            intersectionProbability = 0.2; // 20% chance in suburban areas
        } else {
            intersectionProbability = 0.1; // 10% chance in rural areas
        }
        
        // Don't make first or last waypoint an intersection
        if (waypointIndex == 0 || waypointIndex == totalWaypoints) {
            return false;
        }
        
        return random.nextDouble() < intersectionProbability;
    }

    /**
     * Select a random intersection type
     */
    private String selectIntersectionType() {
        String[] types = {"traffic_light", "stop_sign", "yield", "none"};
        double[] probabilities = {0.4, 0.3, 0.2, 0.1}; // Traffic lights most common
        
        double rand = random.nextDouble();
        double cumulative = 0.0;
        
        for (int i = 0; i < types.length; i++) {
            cumulative += probabilities[i];
            if (rand <= cumulative) {
                return types[i];
            }
        }
        
        return "none";
    }

    /**
     * Generate a realistic street name for the waypoint
     */
    private String generateStreetName(int waypointIndex, int totalWaypoints, Destination destination) {
        if (waypointIndex == totalWaypoints) {
            return destination.name();
        }
        
        // Generate realistic Atlanta-area street names
        String[] streetTypes = {"St", "Ave", "Blvd", "Dr", "Rd", "Pkwy", "Way", "Ln"};
        String[] streetNames = {
            "Peachtree", "Piedmont", "Spring", "West Peachtree", "Juniper", "Cypress",
            "Oak", "Pine", "Maple", "Elm", "Cedar", "Magnolia", "Dogwood", "Azalea",
            "North", "South", "East", "West", "Highland", "Virginia", "Monroe", "Ponce de Leon",
            "Memorial", "DeKalb", "Fulton", "Gwinnett", "Cobb", "Clayton", "Forsyth",
            "Roswell", "Marietta", "Decatur", "Buckhead", "Midtown", "Downtown"
        };
        
        String name = streetNames[random.nextInt(streetNames.length)];
        String type = streetTypes[random.nextInt(streetTypes.length)];
        
        // Add intersection info for some waypoints
        if (random.nextDouble() < 0.3) {
            String cross = streetNames[random.nextInt(streetNames.length)];
            return name + " " + type + " & " + cross + " " + streetTypes[random.nextInt(streetTypes.length)];
        }
        
        return name + " " + type;
    }
}
