package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Destination;
import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


@Service
public class DriverManager {

    private static final Logger logger = LoggerFactory.getLogger(DriverManager.class);
    private final SecureRandom random = new SecureRandom();
    private final List<Driver> drivers = new CopyOnWriteArrayList<>();
    private final FileBasedRouteService routeService;
    private final DestinationRouteService destinationRouteService;
    private final DriverConfigService driverConfigService;

    @Value("${telematics.simulation.driver-count:3}")
    private int driverCount;

    @Value("${telematics.simulation.crash-frequency:50}")
    private int crashFrequency;

    @Value("${telematics.behavior.post-crash-idle-minutes:10}")
    private int postCrashIdleMinutes;

    @Value("${telematics.behavior.random-stop-probability:0.05}")
    private double randomStopProbability;

    @Value("${telematics.behavior.break-duration-minutes:5}")
    private int breakDurationMinutes;

    @Value("${telematics.simulation.max-drivers:0}")
    private int maxDrivers;

    public DriverManager(FileBasedRouteService routeService, DestinationRouteService destinationRouteService, DriverConfigService driverConfigService) {
        this.routeService = routeService;
        this.destinationRouteService = destinationRouteService;
        this.driverConfigService = driverConfigService;
    }

    public void initializeDrivers(String basePolicyId, double baseLatitude, double baseLongitude) {
        drivers.clear();
        
        // Load driver configurations from file
        List<com.insurancemegacorp.telematicsgen.model.DriverConfig> driverConfigs = driverConfigService.getAllDriverConfigs();
        if (maxDrivers > 0 && driverConfigs.size() > maxDrivers) {
            logger.info("🔢 Applying max driver cap: {} (from {} total)", maxDrivers, driverConfigs.size());
            driverConfigs = driverConfigs.subList(0, maxDrivers);
        }
        
        if (driverConfigs.isEmpty()) {
            logger.error("❌ No driver configurations found. Cannot initialize drivers.");
            return;
        }
        
        logger.info("🚗 Initializing {} drivers from file-based configuration...", driverConfigs.size());
        
        for (com.insurancemegacorp.telematicsgen.model.DriverConfig config : driverConfigs) {
            try {
                // Use preferred route if available, otherwise random route
                List<RoutePoint> route;
                if (config.preferredRoute() != null && !config.preferredRoute().isEmpty()) {
                    route = routeService.getRouteByName(config.preferredRoute());
                    if (route == null) {
                        logger.warn("⚠️ Preferred route '{}' not found for driver {}, using random route", 
                            config.preferredRoute(), config.getDriverId());
                        route = routeService.getRandomRoute();
                    }
                } else {
                    route = routeService.getRandomRoute();
                }
                
                // Pick a random point along the route as starting position for better distribution
                int randomIndex = random.nextInt(route.size());
                RoutePoint startPoint = route.get(randomIndex);
                
                // Add small random offset for realistic GPS variation
                double latOffset = (random.nextDouble() - 0.5) * 0.001; // ~100m GPS variation
                double lonOffset = (random.nextDouble() - 0.5) * 0.001;
                double driverLat = startPoint.latitude() + latOffset;
                double driverLon = startPoint.longitude() + lonOffset;
                
                // Create driver with VIN from configuration
                Driver driver = new Driver(config.getDriverId(), config.policyId(), config.vehicleId(), config.vin(), driverLat, driverLon);
                driver.setCurrentRoute(route);
                driver.setCurrentStreet(startPoint.streetName());
                driver.setRouteIndex(randomIndex); // Start at the random point along the route
                driver.setCurrentBearing(0.0); // Will be calculated during movement
                
                // Randomize initial state for more realistic simulation
                initializeRandomDriverState(driver);
                
                drivers.add(driver);
                
                logger.info("🚗 Initialized {} ({}) at {} | Vehicle: {} | Route: {} | State: {} | Speed: {} mph", 
                    config.getDisplayName(),
                    config.vin(),
                    startPoint.streetName(),
                    config.getVehicleDescription(),
                    getRouteDescription(route),
                    driver.getCurrentState(), 
                    String.format("%.1f", driver.getCurrentSpeed()));
                    
            } catch (Exception e) {
                logger.error("❌ Failed to initialize driver {}: {}", config.getDriverId(), e.getMessage(), e);
            }
        }
        
        logger.info("✅ Initialized {} drivers for simulation", drivers.size());
    }

    public List<Driver> getAllDrivers() {
        return List.copyOf(drivers);
    }

    public Driver selectDriverForMessage() {
        if (drivers.isEmpty()) {
            throw new IllegalStateException("No drivers initialized");
        }
        
        // Round-robin selection with some variation
        int index = random.nextInt(drivers.size());
        return drivers.get(index);
    }

    public void updateDriverBehavior(Driver driver) {
        updateDriverState(driver);
        
        if (driver.getCurrentState() == DriverState.DRIVING) {
            updateDriverMovement(driver);
        }
        // Note: Drivers in POST_CRASH_IDLE, PARKED, TRAFFIC_STOP, and BREAK_TIME states
        // maintain their current coordinates and don't move until they resume DRIVING
    }

    private void updateDriverState(Driver driver) {
        long timeInCurrentState = driver.getTimeInCurrentStateSeconds();
        
        switch (driver.getCurrentState()) {
            case POST_CRASH_IDLE -> {
                // Stay idle for configured time after crash
                if (timeInCurrentState >= postCrashIdleMinutes * 60) {
                    logger.info("🏁 {} resuming after {}-minute crash idle period", 
                        driver.getDriverId(), postCrashIdleMinutes);
                    driver.setCurrentState(DriverState.PARKED);
                }
            }
            case PARKED -> {
                // Random chance to start driving
                if (timeInCurrentState > 30 && random.nextDouble() < 0.3) {
                    logger.info("🚙 {} starting to drive", driver.getDriverId());
                    driver.setCurrentState(DriverState.DRIVING);
                }
            }
            case DRIVING -> {
                // Check for crash event
                if (shouldSimulateCrash(driver)) {
                    logger.warn("💥 Driver {} experiencing crash event!", driver.getDriverId());
                    driver.recordCrashEvent();
                    return;
                }
                
                // Random chance to stop for various reasons
                if (random.nextDouble() < randomStopProbability) {
                    DriverState newState = selectRandomStopState();
                    logger.info("🛑 {} stopping: {}", driver.getDriverId(), newState);
                    driver.setCurrentState(newState);
                    driver.setCurrentSpeed(0.0);
                }
            }
            case TRAFFIC_STOP -> {
                // Short stops (traffic lights, etc.)
                if (timeInCurrentState >= 30 + random.nextInt(60)) {
                    logger.info("🚦 {} resuming from traffic stop", driver.getDriverId());
                    driver.setCurrentState(DriverState.DRIVING);
                }
            }
            case BREAK_TIME -> {
                // Longer breaks
                if (timeInCurrentState >= breakDurationMinutes * 60) {
                    logger.info("☕ {} resuming from break", driver.getDriverId());
                    driver.setCurrentState(DriverState.DRIVING);
                }
            }
        }
    }

    private void updateDriverMovement(Driver driver) {
        if (driver.getCurrentRoute() == null || driver.getCurrentRoute().isEmpty()) {
            // Fallback to random movement if no route
            simulateRandomMovement(driver);
            return;
        }
        
        List<RoutePoint> route = driver.getCurrentRoute();
        int currentIndex = driver.getRouteIndex();
        
        // Check if we've reached the end of the route (destination)
        if (currentIndex >= route.size() - 1) {
            // Mark trip as complete and start a new route
            if (driver.getCurrentDestination() != null) {
                logger.info("🏁 {} reached destination: {} (Trip duration: {}min)", 
                    driver.getDriverId(), 
                    driver.getCurrentDestination().name(),
                    driver.getTripDurationSeconds() / 60);
                driver.setTripProgressPercent(100.0);
            }
            assignNewRoute(driver);
            return;
        }
        
        RoutePoint nextPoint = route.get(currentIndex + 1);
        
        // Calculate bearing to next point
        double bearing = calculateBearing(
            driver.getCurrentLatitude(), driver.getCurrentLongitude(),
            nextPoint.latitude(), nextPoint.longitude()
        );
        driver.setCurrentBearing(bearing);
        
        // Move toward next route point
        double distanceToNext = calculateDistance(
            driver.getCurrentLatitude(), driver.getCurrentLongitude(),
            nextPoint.latitude(), nextPoint.longitude()
        );
        
        // Smaller threshold for smoother movement (5 meters instead of 10)
        if (distanceToNext < 0.00005) { 
            driver.setCurrentLatitude(nextPoint.latitude());
            driver.setCurrentLongitude(nextPoint.longitude());
            driver.setCurrentStreet(nextPoint.streetName());
            driver.setRouteIndex(currentIndex + 1);
            
            // Update trip progress
            updateTripProgress(driver);
            
            // Adjust speed based on route point characteristics
            updateSpeedForRoutePoint(driver, nextPoint);
        } else {
            // Move incrementally toward next point with smaller steps for smoother movement
            double speed = driver.getCurrentSpeed();
            // Reduced step size for smoother movement (was /111000.0, now /222000.0)
            double stepSize = (speed * 0.44704) / 222000.0; 
            
            double latStep = (nextPoint.latitude() - driver.getCurrentLatitude()) * stepSize / distanceToNext;
            double lonStep = (nextPoint.longitude() - driver.getCurrentLongitude()) * stepSize / distanceToNext;
            
            driver.setCurrentLatitude(driver.getCurrentLatitude() + latStep);
            driver.setCurrentLongitude(driver.getCurrentLongitude() + lonStep);
            
            // Update trip progress incrementally
            updateTripProgress(driver);
        }
        
        // Update speed with variation
        updateDrivingSpeed(driver);
    }
    
    private void simulateRandomMovement(Driver driver) {
        // Fallback random movement (original logic)
        double latMovement = (random.nextDouble() - 0.5) * 0.0001;
        double lonMovement = (random.nextDouble() - 0.5) * 0.0001;
        
        driver.setCurrentLatitude(driver.getCurrentLatitude() + latMovement);
        driver.setCurrentLongitude(driver.getCurrentLongitude() + lonMovement);
        
        updateDrivingSpeed(driver);
    }
    
    private void updateDrivingSpeed(Driver driver) {
        if (driver.getCurrentSpeed() == 0.0) {
            driver.setCurrentSpeed(15.0 + random.nextDouble() * 30.0); // Start moving
        } else {
            // Vary speed slightly
            double speedVariation = (random.nextDouble() - 0.5) * 5.0;
            double newSpeed = Math.max(10.0, Math.min(50.0, driver.getCurrentSpeed() + speedVariation));
            driver.setCurrentSpeed(newSpeed);
        }
    }
    
    private void updateSpeedForRoutePoint(Driver driver, RoutePoint point) {
        if (point.isIntersection()) {
            // Slow down at intersections
            driver.setCurrentSpeed(Math.max(10.0, driver.getCurrentSpeed() * 0.7));
        } else {
            // Use route speed limit with some variation
            double targetSpeed = point.speedLimitMph() + (random.nextDouble() - 0.5) * 10.0;
            driver.setCurrentSpeed(Math.max(15.0, Math.min(55.0, targetSpeed)));
        }
    }
    
    private void assignNewRoute(Driver driver) {
        // Generate a random destination within 40-mile radius
        Destination destination = destinationRouteService.generateRandomDestination();
        
        // Generate route from current location to destination
        List<RoutePoint> newRoute = destinationRouteService.generateRouteToDestination(
            driver.getCurrentLatitude(), driver.getCurrentLongitude(), destination);
        
        // Update driver with new route and destination
        driver.setCurrentRoute(newRoute);
        driver.setCurrentDestination(destination);
        driver.setRouteIndex(0);
        
        logger.info("🛣️  {} assigned new destination: {} ({:.1f} miles away)", 
            driver.getDriverId(), destination.name(), destination.distanceFromOriginMiles());
    }
    
    /**
     * Update the driver's trip progress based on current position relative to route
     */
    private void updateTripProgress(Driver driver) {
        if (driver.getCurrentRoute() == null || driver.getCurrentDestination() == null) {
            return;
        }
        
        List<RoutePoint> route = driver.getCurrentRoute();
        int currentIndex = driver.getRouteIndex();
        
        // Calculate progress as percentage of route completed
        double progressPercent = ((double) currentIndex / (route.size() - 1)) * 100.0;
        driver.setTripProgressPercent(Math.min(progressPercent, 100.0));
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    
    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double deltaLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        
        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360; // Normalize to 0-360
    }

    private boolean shouldSimulateCrash(Driver driver) {
        long messageCount = driver.getMessageCount();
        long timeSinceCrash = driver.getTimeSinceCrashSeconds();
        
        // Ensure minimum time between crashes (30 minutes)
        if (timeSinceCrash < 1800) {
            return false;
        }
        
        // Require at least 10 messages before first crash
        if (messageCount < 10) {
            return false;
        }
        
        // Low probability crash simulation - realistic frequency
        return messageCount % 100 == 0 && random.nextDouble() < 0.01;
    }
    
    /**
     * Manually trigger an accident for a specific driver (for demo purposes)
     */
    public boolean triggerDemoAccident(String driverId) {
        Driver driver = drivers.stream()
            .filter(d -> d.getDriverId().equals(driverId))
            .findFirst()
            .orElse(null);
            
        if (driver == null) {
            logger.warn("⚠️ Cannot trigger accident: Driver {} not found", driverId);
            return false;
        }
        
        // Don't crash if already in crash state
        if (driver.getCurrentState().name().contains("CRASH")) {
            logger.warn("⚠️ Cannot trigger accident: Driver {} already in crash state", driverId);
            return false;
        }
        
        // Don't crash too soon after the last crash
        if (driver.getTimeSinceCrashSeconds() < 300) { // 5 minutes minimum for demo
            logger.warn("⚠️ Cannot trigger accident: Driver {} crashed too recently", driverId);
            return false;
        }
        
        // Trigger the crash by setting state and recording event
        driver.setCurrentState(DriverState.POST_CRASH_IDLE);
        driver.recordCrashEvent();
        logger.info("🚨 Demo accident triggered for driver {}", driverId);
        return true;
    }

    private void initializeRandomDriverState(Driver driver) {
        double rand = random.nextDouble();
        
        if (rand < 0.4) {
            // 40% chance - Start driving
            driver.setCurrentState(DriverState.DRIVING);
            driver.setCurrentSpeed(20.0 + random.nextDouble() * 25.0); // 20-45 mph
            logger.debug("Driver {} initialized DRIVING at {:.1f} mph", 
                driver.getDriverId(), driver.getCurrentSpeed());
        } else if (rand < 0.7) {
            // 30% chance - Start parked
            driver.setCurrentState(DriverState.PARKED);
            driver.setCurrentSpeed(0.0);
            logger.debug("Driver {} initialized PARKED", driver.getDriverId());
        } else if (rand < 0.85) {
            // 15% chance - At a traffic stop
            driver.setCurrentState(DriverState.TRAFFIC_STOP);
            driver.setCurrentSpeed(0.0);
            logger.debug("Driver {} initialized at TRAFFIC_STOP", driver.getDriverId());
        } else {
            // 15% chance - On a break
            driver.setCurrentState(DriverState.BREAK_TIME);
            driver.setCurrentSpeed(0.0);
            logger.debug("Driver {} initialized on BREAK_TIME", driver.getDriverId());
        }
    }

    private DriverState selectRandomStopState() {
        double rand = random.nextDouble();
        if (rand < 0.4) {
            return DriverState.TRAFFIC_STOP;
        } else if (rand < 0.7) {
            return DriverState.PARKED;
        } else {
            return DriverState.BREAK_TIME;
        }
    }

    private String getRouteDescription(List<RoutePoint> route) {
        if (route == null || route.isEmpty()) {
            return "No route";
        }
        
        RoutePoint start = route.get(0);
        RoutePoint end = route.get(route.size() - 1);
        
        return String.format("%s → %s", 
            start.streetName().split(" & ")[0], // Extract main street name
            end.streetName().split(" & ")[0]
        );
    }

    public int getDriverCount() {
        return drivers.size();
    }

    public void logDriverStates() {
        logger.info("🚗 Driver Status Summary:");
        drivers.forEach(driver -> {
            String crashInfo = driver.getLastCrashTime() != null ? 
                String.format("Last crash: %dmin ago", driver.getTimeSinceCrashSeconds() / 60) : 
                "No crashes";
            logger.info("   {} | State: {} | Speed: {:.1f} mph | Messages: {} | {}", 
                driver.getDriverId(), 
                driver.getCurrentState(), 
                driver.getCurrentSpeed(),
                driver.getMessageCount(),
                crashInfo);
        });
    }
}