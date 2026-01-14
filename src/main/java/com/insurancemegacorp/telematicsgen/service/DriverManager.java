package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;


@Service
public class DriverManager {

    private static final Logger logger = LoggerFactory.getLogger(DriverManager.class);
    private final SecureRandom random = new SecureRandom();
    private final List<Driver> drivers = new CopyOnWriteArrayList<>();
    private final FileBasedRouteService routeService;
    private final DriverConfigService driverConfigService;
    private final DailyRoutineService dailyRoutineService;

    @Value("${telematics.behavior.post-crash-idle-minutes:10}")
    private int postCrashIdleMinutes;

    @Value("${telematics.behavior.random-stop-probability:0.05}")
    private double randomStopProbability;

    @Value("${telematics.behavior.break-duration-minutes:5}")
    private int breakDurationMinutes;

    @Value("${telematics.simulation.max-drivers:0}")
    private int maxDrivers;

    // Time-based behavior configuration
    @Value("${telematics.behavior.night-start-hour:20}")
    private int nightStartHour;

    @Value("${telematics.behavior.night-end-hour:6}")
    private int nightEndHour;

    @Value("${telematics.behavior.night-driving-reduction:0.7}")
    private double nightDrivingReduction;

    @Value("${telematics.behavior.night-parked-probability:0.85}")
    private double nightParkedProbability;

    @Value("${telematics.behavior.peak-hours:#{T(java.util.Arrays).asList(7,8,17,18)}}")
    private List<Integer> peakHours;

    @Value("${telematics.behavior.peak-driving-boost:1.5}")
    private double peakDrivingBoost;

    private volatile boolean randomAccidentsEnabled = false;

    public DriverManager(FileBasedRouteService routeService, DriverConfigService driverConfigService, DailyRoutineService dailyRoutineService) {
        this.routeService = routeService;
        this.driverConfigService = driverConfigService;
        this.dailyRoutineService = dailyRoutineService;
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
                if (route.isEmpty()) {
                    logger.error("❌ Route is empty for driver {}", config.getDriverId());
                    continue; // Skip this driver
                }
                int randomIndex = random.nextInt(route.size());
                RoutePoint startPoint = route.get(randomIndex);
                
                // Add small random offset for realistic GPS variation
                double latOffset = (random.nextDouble() - 0.5) * 0.001; // ~100m GPS variation
                double lonOffset = (random.nextDouble() - 0.5) * 0.001;
                double driverLat = startPoint.latitude() + latOffset;
                double driverLon = startPoint.longitude() + lonOffset;
                
                // Create driver with VIN from configuration
                Driver driver = new Driver(config.getDriverId(), config.policyId(), config.vehicleId(), config.vin(), driverLat, driverLon, config.aggressive());
                driver.setCurrentRoute(route);
                driver.setCurrentStreet(startPoint.streetName());
                driver.setSpeedLimit(startPoint.speedLimit());
                driver.setRouteIndex(randomIndex); // Start at the random point along the route
                driver.setCurrentBearing(0.0); // Will be calculated during movement
                
                // Randomize initial state for more realistic simulation
                initializeRandomDriverState(driver);
                
                // Initialize daily routine if available
                initializeDriverDailyRoutine(driver);
                
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
        int index = ThreadLocalRandom.current().nextInt(drivers.size());
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
                // Time-based chance to start driving
                double drivingProbability = calculateDrivingProbability();
                if (timeInCurrentState > 30 && ThreadLocalRandom.current().nextDouble() < drivingProbability) {
                    logger.info("🚙 {} starting to drive ({})", driver.getDriverId(), 
                        isNightTime() ? "night driving" : isPeakHour() ? "peak hour" : "normal hours");
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
                
                // Time-based chance to stop for various reasons
                double stopProbability = calculateStopProbability();
                if (ThreadLocalRandom.current().nextDouble() < stopProbability) {
                    DriverState newState = selectRandomStopState();
                    logger.info("🛑 {} stopping: {} ({})", driver.getDriverId(), newState,
                        isNightTime() ? "night parking" : "normal stop");
                    driver.setCurrentState(newState);
                    driver.setCurrentSpeed(0.0);
                }
            }
            case TRAFFIC_STOP -> {
                // Short stops (traffic lights, etc.)
                if (timeInCurrentState >= 30 + ThreadLocalRandom.current().nextInt(60)) {
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

        RoutePoint currentPoint = route.get(currentIndex);
        driver.setCurrentStreet(currentPoint.streetName());
        driver.setSpeedLimit(currentPoint.speedLimit());

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
        if (point.hasTrafficLight()) {
            driver.setCurrentSpeed(Math.max(10.0, driver.getCurrentSpeed() * 0.7));
            return;
        }

        double baseVariation = (random.nextDouble() - 0.5) * 10.0;
        double speedingProbability = driver.isAggressive() ? 0.2 : 0.1;
        double maxSpeed = driver.isAggressive() ? 95.0 : 85.0;

        double targetSpeed = point.speedLimit() + baseVariation;
        if (random.nextDouble() < speedingProbability) {
            targetSpeed += 15.0 + random.nextDouble() * 10.0;
        }

        driver.setCurrentSpeed(Math.max(15.0, Math.min(maxSpeed, targetSpeed)));
    }
    
    private void assignNewRoute(Driver driver) {
        // Routes are circular - driver completed their loop, restart from beginning
        List<RoutePoint> currentRoute = driver.getCurrentRoute();

        if (currentRoute == null || currentRoute.isEmpty()) {
            logger.warn("⚠️ No route available for driver {}", driver.getDriverId());
            return;
        }

        // Reset to beginning of the same circular route
        driver.setRouteIndex(0);
        driver.setCurrentDestination(null);

        // Update speed limit and street from first route point
        RoutePoint startPoint = currentRoute.get(0);
        driver.setSpeedLimit(startPoint.speedLimit());
        driver.setCurrentStreet(startPoint.streetName());

        String routeDescription = getRouteDescription(currentRoute);
        logger.info("🔄 {} restarting circular route: {} ({} waypoints)",
            driver.getDriverId(), routeDescription, currentRoute.size());
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
        // Only allow random crashes if the feature is enabled
        if (!randomAccidentsEnabled) {
            return false;
        }
        
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
        
        // Very low probability crash simulation - more realistic frequency
        // Approximately 1 crash per 10,000 messages (roughly once every 1.4 hours at 500ms intervals)
        return random.nextDouble() < 0.0001;
    }
    
    /**
     * Manually trigger an accident for a specific driver (for demo purposes).
     * If the driver is stopped at a traffic light, they will be rear-ended.
     * Otherwise, a random accident type is assigned.
     */
    public boolean triggerDemoAccident(int driverId) {
        Driver driver = drivers.stream()
            .filter(d -> d.getDriverId() == driverId)
            .findFirst()
            .orElse(null);

        if (driver == null) {
            logger.warn("⚠️ Cannot trigger accident: Driver {} not found", driverId);
            return false;
        }

        // Don't crash if already in crash state
        if (driver.getCurrentState() == DriverState.POST_CRASH_IDLE) {
            logger.warn("⚠️ Cannot trigger accident: Driver {} already in crash state", driverId);
            return false;
        }

        // Don't crash too soon after the last crash
        if (driver.getTimeSinceCrashSeconds() < 300) { // 5 minutes minimum for demo
            logger.warn("⚠️ Cannot trigger accident: Driver {} crashed too recently", driverId);
            return false;
        }

        // Determine accident type based on current state
        boolean stoppedAtLight = (driver.getCurrentState() == DriverState.TRAFFIC_STOP);
        String accidentScenario = stoppedAtLight ? "REAR-ENDED at traffic stop" : "while driving";

        // Log pre-crash info
        logger.info("🚨 Triggering accident for driver {} {} at {} mph on {} (speed limit: {} mph, state: {})",
            driverId, accidentScenario, String.format("%.1f", driver.getCurrentSpeed()),
            driver.getCurrentStreet(), driver.getSpeedLimit(), driver.getCurrentState());

        // Trigger the crash by setting state and recording event
        driver.setCurrentState(DriverState.POST_CRASH_IDLE);
        driver.recordCrashEvent();
        return true;
    }

    private void initializeRandomDriverState(Driver driver) {
        // All drivers start DRIVING for demo purposes - they can take breaks and
        // stop at lights naturally as the simulation progresses
        driver.setCurrentState(DriverState.DRIVING);
        driver.setCurrentSpeed(20.0 + random.nextDouble() * 25.0); // 20-45 mph
        logger.debug("Driver {} initialized DRIVING at {:.1f} mph",
            driver.getDriverId(), driver.getCurrentSpeed());
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

    /**
     * Get a description of a route (start → end)
     */
    public String getRouteDescription(Driver driver) {
        if (driver == null || driver.getCurrentRoute() == null || driver.getCurrentRoute().isEmpty()) {
            return "No route";
        }
        return getRouteDescription(driver.getCurrentRoute());
    }

    private String getRouteDescription(List<RoutePoint> route) {
        if (route == null || route.isEmpty()) {
            return "No route";
        }

        RoutePoint start = route.get(0);
        RoutePoint end = route.get(route.size() - 1);

        return String.format("%s → %s",
            start.streetName().split(" & ")[0],
            end.streetName().split(" & ")[0]
        );
    }

    public int getDriverCount() {
        return drivers.size();
    }

    public void logDriverStates() {
        logger.info("🚗 Driver Status Summary - {}", getCurrentTimeStatus());
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
        
        // Log activity summary based on time
        long drivingCount = drivers.stream().mapToLong(d -> d.getCurrentState() == DriverState.DRIVING ? 1 : 0).sum();
        long parkedCount = drivers.size() - drivingCount;
        logger.info("📊 Activity Summary: {} driving, {} parked ({})", 
            drivingCount, parkedCount, getCurrentTimeStatus());
    }

    public void setRandomAccidentsEnabled(boolean enabled) {
        this.randomAccidentsEnabled = enabled;
        logger.info("🎲 Random accidents {}", enabled ? "enabled" : "disabled");
    }

    public boolean isRandomAccidentsEnabled() {
        return randomAccidentsEnabled;
    }

    /**
     * Signal all drivers to start driving to their next route.
     * Parked or idle drivers will be assigned a new destination and begin driving.
     * @return the number of drivers that started driving
     */
    public int startAllDriving() {
        int startedCount = 0;
        for (Driver driver : drivers) {
            if (driver.getCurrentState() != DriverState.DRIVING) {
                // Assign a new destination and route
                assignNewRoute(driver);
                // Set to driving state
                driver.setCurrentState(DriverState.DRIVING);
                // Give initial speed
                driver.setCurrentSpeed(15.0 + random.nextDouble() * 30.0);
                startedCount++;
            }
        }
        logger.info("🚦 Started {} drivers on their routes", startedCount);
        return startedCount;
    }

    // Time-based behavior methods
    
    /**
     * Check if current time is during night hours (reduced activity)
     * Disabled - always returns false to keep drivers active for demos
     */
    private boolean isNightTime() {
        return false;
    }
    
    /**
     * Check if current time is during peak driving hours
     */
    private boolean isPeakHour() {
        int currentHour = LocalTime.now().getHour();
        return peakHours.contains(currentHour);
    }
    
    /**
     * Calculate probability of starting to drive based on time of day
     */
    private double calculateDrivingProbability() {
        double baseProbability = 0.3; // 30% base chance
        
        if (isNightTime()) {
            // Significantly reduce driving at night
            baseProbability *= (1.0 - nightDrivingReduction);
            logger.debug("🌙 Night time driving reduction applied: {}", baseProbability);
        } else if (isPeakHour()) {
            // Increase driving during peak hours
            baseProbability *= peakDrivingBoost;
            logger.debug("🚗 Peak hour driving boost applied: {}", baseProbability);
        }
        
        return Math.min(1.0, Math.max(0.0, baseProbability));
    }
    
    /**
     * Calculate probability of stopping/parking based on time of day
     */
    private double calculateStopProbability() {
        double baseStopProbability = randomStopProbability;
        
        if (isNightTime()) {
            // Much higher chance to park at night
            baseStopProbability = nightParkedProbability;
            logger.debug("🌙 Night time parking probability: {}", baseStopProbability);
        } else if (isPeakHour()) {
            // Slightly lower chance to stop during peak hours (people want to get to work/home)
            baseStopProbability *= 0.7;
            logger.debug("🚗 Peak hour reduced stop probability: {}", baseStopProbability);
        }
        
        return Math.min(1.0, Math.max(0.0, baseStopProbability));
    }
    
    /**
     * Get current time status for logging and monitoring
     */
    public String getCurrentTimeStatus() {
        if (isNightTime()) {
            return "🌙 NIGHT MODE - Reduced activity";
        } else if (isPeakHour()) {
            return "🚗 PEAK HOURS - Increased activity";
        } else {
            return "☀️ NORMAL HOURS - Standard activity";
        }
    }
    
    // Daily routine methods
    
    /**
     * Initialize a driver's daily routine if configured
     */
    private void initializeDriverDailyRoutine(Driver driver) {
        if (!dailyRoutineService.isAvailable()) {
            logger.debug("📋 Daily routine system not available for driver {}", driver.getDriverId());
            return;
        }
        
        Optional<com.insurancemegacorp.telematicsgen.model.DailyRoutine> routineOpt = 
            dailyRoutineService.getRoutineForDriver(driver.getDriverId());
            
        if (routineOpt.isPresent()) {
            com.insurancemegacorp.telematicsgen.model.DailyRoutine routine = routineOpt.get();
            
            // Generate today's sequence (90% standard, 10% random)
            List<String> dailySequence = dailyRoutineService.generateDailySequence(driver.getDriverId());
            
            logger.info("📋 Driver {} daily routine: {} → {} locations → {}", 
                driver.getDriverId(),
                routine.baseLocation().name(),
                dailySequence.size() - 2, // Exclude start and end BASE
                routine.baseLocation().name());
                
            // Store the sequence in driver metadata (we'll add this to Driver model later)
            // For now, just log the initialization
            logger.debug("🗺️ Sequence: {}", dailySequence);
        } else {
            logger.debug("📋 No daily routine configured for driver {}", driver.getDriverId());
        }
    }
    
    /**
     * Get a driver's current daily routine if available
     */
    public Optional<com.insurancemegacorp.telematicsgen.model.DailyRoutine> getDriverDailyRoutine(int driverId) {
        return dailyRoutineService.getRoutineForDriver(driverId);
    }
    
    /**
     * Generate a new daily sequence for a driver (for daily reset)
     */
    public List<String> generateNewDailySequence(int driverId) {
        return dailyRoutineService.generateDailySequence(driverId);
    }
    
    /**
     * Check if daily routine system is enabled
     */
    public boolean isDailyRoutineSystemEnabled() {
        return dailyRoutineService.isAvailable();
    }
}