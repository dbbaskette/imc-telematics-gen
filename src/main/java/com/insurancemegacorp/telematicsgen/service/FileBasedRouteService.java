package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * New RouteService that loads routes from JSON files instead of hardcoded data
 */
@Service
public class FileBasedRouteService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileBasedRouteService.class);
    private final Random random = new Random();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<RoutePoint>> routes = new HashMap<>();
    
    @PostConstruct
    public void loadRoutes() {
        logger.info("🗂️ Loading routes from files...");
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            
            // Load routes from both directories
            Resource[] basicRoutes = resolver.getResources("classpath:routes/*.json");
            Resource[] dailyRoutes = resolver.getResources("classpath:routes/daily/*.json");
            
            // Combine all route files
            List<Resource> allRoutes = new ArrayList<>();
            Collections.addAll(allRoutes, basicRoutes);
            Collections.addAll(allRoutes, dailyRoutes);
            
            if (allRoutes.isEmpty()) {
                logger.warn("⚠️ No route files found in classpath:routes/ or classpath:routes/daily/");
                logger.warn("⚠️ Please run RouteGenerator utility to create route files");
                loadFallbackRoutes();
                return;
            }
            
            logger.info("📁 Found {} basic routes and {} daily routine routes", basicRoutes.length, dailyRoutes.length);
            
            for (Resource routeFile : allRoutes) {
                try {
                    loadRouteFromFile(routeFile);
                } catch (Exception e) {
                    logger.error("❌ Failed to load route file: {}", routeFile.getFilename(), e);
                }
            }
            
            logger.info("✅ Loaded {} routes successfully", routes.size());
                
        } catch (IOException e) {
            logger.error("❌ Failed to scan for route files", e);
            loadFallbackRoutes();
        }
    }
    
    private String truncateStreet(String streetName) {
        if (streetName == null) return "Unknown";
        return streetName.length() > 30 ? streetName.substring(0, 30) + "..." : streetName;
    }
    
    private void loadRouteFromFile(Resource routeFile) throws IOException {
        JsonNode routeData = mapper.readTree(routeFile.getInputStream());
        
        String routeName = routeData.get("name").asText();
        String description = routeData.get("description").asText();
        JsonNode waypoints = routeData.get("waypoints");
        
        List<RoutePoint> routePoints = new ArrayList<>();
        
        for (JsonNode waypoint : waypoints) {
            double latitude = waypoint.get("latitude").asDouble();
            double longitude = waypoint.get("longitude").asDouble();
            String streetName = waypoint.get("street_name").asText();
            int speedLimit = waypoint.get("speed_limit").asInt();
            boolean hasTrafficLight = waypoint.get("has_traffic_light").asBoolean();
            String trafficControl = waypoint.get("traffic_control").asText();
            
            routePoints.add(new RoutePoint(latitude, longitude, streetName, 
                                         speedLimit, hasTrafficLight, trafficControl));
        }
        
        routes.put(routeName, routePoints);
        logger.debug("📁 Loaded route '{}': {}", routeName, description);
    }
    
    /**
     * Fallback routes in case no route files are found
     */
    private void loadFallbackRoutes() {
        logger.info("🔄 Loading fallback hardcoded routes...");
        
        // Simplified fallback route for testing
        routes.put("fallback_atlanta", List.of(
            new RoutePoint(33.7490, -84.3880, "Downtown Atlanta", 35, true, "traffic_light"),
            new RoutePoint(33.7500, -84.3890, "Midtown Atlanta", 35, false, "none"),
            new RoutePoint(33.7510, -84.3900, "Buckhead Atlanta", 45, true, "traffic_light")
        ));
        
        logger.warn("⚠️ Using fallback routes. Generate proper route files for better simulation.");
    }
    
    public List<RoutePoint> getRandomRoute() {
        if (routes.isEmpty()) {
            throw new IllegalStateException("No routes available! Please generate route files using RouteGenerator utility.");
        }
        
        String[] routeNames = routes.keySet().toArray(new String[0]);
        String selectedRoute = routeNames[random.nextInt(routeNames.length)];
        
        List<RoutePoint> route = routes.get(selectedRoute);
        logger.debug("🎲 Selected random route: {} ({} waypoints)", selectedRoute, route.size());
        
        return new ArrayList<>(route); // Return copy to avoid modification
    }
    
    public List<RoutePoint> getRouteByName(String routeName) {
        List<RoutePoint> route = routes.get(routeName);
        if (route == null) {
            logger.warn("⚠️ Route '{}' not found. Available routes: {}", routeName, routes.keySet());
            return getRandomRoute(); // Fallback to random route
        }
        return new ArrayList<>(route);
    }
    
    public Set<String> getAvailableRoutes() {
        return new HashSet<>(routes.keySet());
    }
    
    public int getRouteCount() {
        return routes.size();
    }
    
    /**
     * Get a route for a specific driver's daily routine segment
     */
    public List<RoutePoint> getDailyRouteForDriver(String driverName, String fromLocation, String toLocation) {
        // Normalize driver name (lowercase, underscores)
        String normalizedDriverName = driverName.toLowerCase().replace(" ", "_");
        
        // Try exact match first
        String routeKey = String.format("%s_%s_to_%s", normalizedDriverName, 
            fromLocation.toLowerCase().replace(" ", "_").replace("-", "_"),
            toLocation.toLowerCase().replace(" ", "_").replace("-", "_"));
            
        List<RoutePoint> route = routes.get(routeKey);
        if (route != null) {
            logger.debug("🎯 Found daily route: {}", routeKey);
            return new ArrayList<>(route);
        }
        
        // Try partial matches
        String partialPattern = normalizedDriverName + "_";
        Optional<String> matchingRoute = routes.keySet().stream()
            .filter(key -> key.startsWith(partialPattern))
            .filter(key -> key.contains(fromLocation.toLowerCase().replace(" ", "_")) && 
                          key.contains(toLocation.toLowerCase().replace(" ", "_")))
            .findFirst();
            
        if (matchingRoute.isPresent()) {
            logger.debug("🔍 Found partial match route: {}", matchingRoute.get());
            return new ArrayList<>(routes.get(matchingRoute.get()));
        }
        
        logger.warn("⚠️ No daily route found for {}: {} → {}. Using random route.", 
            driverName, fromLocation, toLocation);
        return getRandomRoute();
    }
    
    /**
     * Check if daily routes are available for a driver
     */
    public boolean hasDailyRoutesForDriver(String driverName) {
        String normalizedDriverName = driverName.toLowerCase().replace(" ", "_");
        return routes.keySet().stream()
            .anyMatch(key -> key.startsWith(normalizedDriverName + "_"));
    }
}