package com.insurancemegacorp.telematicsgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.telematicsgen.model.DailyRoutine;
import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.*;

/**
 * Service for managing driver daily routines.
 * Each driver has a base location (home) and 4 remote locations they visit in patterns.
 */
@Service
public class DailyRoutineService {
    
    private static final Logger logger = LoggerFactory.getLogger(DailyRoutineService.class);
    
    private final List<DailyRoutine> dailyRoutines = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final FileBasedRouteService routeService;
    
    public DailyRoutineService(FileBasedRouteService routeService) {
        this.routeService = routeService;
        loadDailyRoutines();
    }
    
    /**
     * Load daily routines from JSON configuration file
     */
    private void loadDailyRoutines() {
        try (InputStream is = getClass().getResourceAsStream("/daily-routines.json")) {
            if (is == null) {
                logger.warn("⚠️ daily-routines.json not found, daily routine system disabled");
                return;
            }
            
            JsonNode rootNode = objectMapper.readTree(is);
            JsonNode routinesArray = rootNode.get("daily_routines");
            
            if (routinesArray != null && routinesArray.isArray()) {
                for (JsonNode routineNode : routinesArray) {
                    DailyRoutine routine = objectMapper.treeToValue(routineNode, DailyRoutine.class);
                    dailyRoutines.add(routine);
                }
            }
            
            logger.info("✅ Loaded {} daily routines", dailyRoutines.size());
            
        } catch (IOException e) {
            logger.error("❌ Failed to load daily routines: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get daily routine for a specific driver
     */
    public Optional<DailyRoutine> getRoutineForDriver(int driverId) {
        return dailyRoutines.stream()
            .filter(routine -> routine.driverId() == driverId)
            .findFirst();
    }
    
    /**
     * Generate a daily sequence for a driver.
     * 90% chance of standard sequence, 10% chance of randomized order.
     */
    public List<String> generateDailySequence(int driverId) {
        Optional<DailyRoutine> routineOpt = getRoutineForDriver(driverId);
        if (routineOpt.isEmpty()) {
            logger.warn("⚠️ No daily routine found for driver {}", driverId);
            return Collections.emptyList();
        }
        
        DailyRoutine routine = routineOpt.get();
        List<String> remoteSequence;
        
        // 90% chance of standard sequence, 10% chance of randomized
        if (random.nextDouble() < routine.sequenceProbability()) {
            remoteSequence = new ArrayList<>(routine.standardSequence());
            logger.debug("📋 Driver {} following standard sequence: {}", driverId, remoteSequence);
        } else {
            remoteSequence = new ArrayList<>(routine.standardSequence());
            Collections.shuffle(remoteSequence, random);
            logger.info("🔀 Driver {} using randomized sequence: {}", driverId, remoteSequence);
        }
        
        return routine.getFullSequence(remoteSequence);
    }
    
    /**
     * Get all available daily routines
     */
    public List<DailyRoutine> getAllRoutines() {
        return Collections.unmodifiableList(dailyRoutines);
    }
    
    /**
     * Check if daily routine system is available
     */
    public boolean isAvailable() {
        return !dailyRoutines.isEmpty();
    }
    
    /**
     * Get location coordinates for a driver's routine stop
     */
    public Optional<DailyRoutine.Location> getLocationCoordinates(int driverId, String locationId) {
        Optional<DailyRoutine> routineOpt = getRoutineForDriver(driverId);
        if (routineOpt.isEmpty()) {
            return Optional.empty();
        }
        
        DailyRoutine routine = routineOpt.get();
        
        if ("BASE".equals(locationId)) {
            return Optional.of(routine.baseLocation());
        }
        
        DailyRoutine.RemoteLocation remoteLocation = routine.getLocationById(locationId);
        return remoteLocation != null ? Optional.of(remoteLocation.toLocation()) : Optional.empty();
    }
    
    /**
     * Get the next route segment for a driver based on their daily routine
     */
    public Optional<List<RoutePoint>> getNextRouteSegment(int driverId, String currentLocation, String nextLocation) {
        Optional<DailyRoutine> routineOpt = getRoutineForDriver(driverId);
        if (routineOpt.isEmpty()) {
            logger.debug("🔍 No daily routine found for driver {}, using random route", driverId);
            return Optional.empty();
        }
        
        DailyRoutine routine = routineOpt.get();
        String driverName = routine.driverName();
        
        // Check if route service has routes for this driver
        if (!routeService.hasDailyRoutesForDriver(driverName)) {
            logger.warn("⚠️ No daily routes found for driver {} ({}), using random route", driverId, driverName);
            return Optional.empty();
        }
        
        // Get route from FileBasedRouteService
        List<RoutePoint> route = routeService.getDailyRouteForDriver(driverName, currentLocation, nextLocation);
        logger.info("🗺️ Driver {} ({}) following route: {} → {} ({} waypoints)", 
            driverId, driverName, currentLocation, nextLocation, route.size());
        
        return Optional.of(route);
    }
    
    /**
     * Get the next location in a driver's daily sequence
     */
    public Optional<String> getNextLocationInSequence(int driverId, String currentLocation) {
        List<String> sequence = generateDailySequence(driverId);
        if (sequence.isEmpty()) {
            return Optional.empty();
        }
        
        // Find current location in sequence
        int currentIndex = sequence.indexOf(currentLocation);
        if (currentIndex == -1) {
            // If current location not found, start from beginning
            return sequence.isEmpty() ? Optional.empty() : Optional.of(sequence.get(0));
        }
        
        // Get next location (wrap around to start if at end)
        int nextIndex = (currentIndex + 1) % sequence.size();
        return Optional.of(sequence.get(nextIndex));
    }
}
