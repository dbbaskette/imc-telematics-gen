package com.acme.insurance.telematics.service;

import com.acme.insurance.telematics.model.RoutePoint;
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
            Resource[] routeFiles = resolver.getResources("classpath:routes/*.json");
            
            if (routeFiles.length == 0) {
                logger.warn("⚠️ No route files found in classpath:routes/");
                logger.warn("⚠️ Please run RouteGenerator utility to create route files");
                loadFallbackRoutes();
                return;
            }
            
            for (Resource routeFile : routeFiles) {
                try {
                    loadRouteFromFile(routeFile);
                } catch (Exception e) {
                    logger.error("❌ Failed to load route file: {}", routeFile.getFilename(), e);
                }
            }
            
            logger.info("✅ Loaded {} routes successfully", routes.size());
            logger.info("🛣️  Available Routes:");
            routes.keySet().stream()
                .sorted()
                .forEach(routeName -> {
                    List<RoutePoint> route = routes.get(routeName);
                    String startStreet = route.get(0).streetName();
                    String endStreet = route.get(route.size() - 1).streetName();
                    
                    // Handle routes with missing or empty names
                    String displayName = (routeName == null || routeName.trim().isEmpty()) 
                        ? "unnamed_route" 
                        : routeName;
                    
                    logger.info("   🚗 {} ({} waypoints) | {} → {}", 
                        displayName, 
                        route.size(),
                        truncateStreet(startStreet),
                        truncateStreet(endStreet));
                });
                
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
}