package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverLocationUpdate;
import com.insurancemegacorp.telematicsgen.model.FlatTelematicsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WebSocketBroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcastService.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final DriverManager driverManager;

    public WebSocketBroadcastService(SimpMessagingTemplate messagingTemplate, DriverManager driverManager) {
        this.messagingTemplate = messagingTemplate;
        this.driverManager = driverManager;
    }

    public void broadcastDriverUpdate(Driver driver, FlatTelematicsMessage message) {
        try {
            String routeDescription = getRouteDescription(driver);
            // Use pre-calculated G-force from the flat message instead of recalculating
            double gForce = message.gForce();
            
            // Check if this might be a crash event based on driver state (for dashboard display only)
            boolean isInCrashState = driver.getCurrentState().name().contains("CRASH") || 
                                   driver.getCurrentState().name().contains("POST_CRASH_IDLE");
            
            DriverLocationUpdate update = new DriverLocationUpdate(
                driver.getDriverId(),
                driver.getPolicyId(),
                driver.getVehicleId(),
                driver.getCurrentLatitude(),
                driver.getCurrentLongitude(),
                driver.getCurrentBearing(),
                driver.getCurrentSpeed(),
                driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
                driver.getCurrentState(),
                routeDescription,
                isInCrashState,
                gForce,
                Instant.now()
            );

            // Only broadcast every few messages to reduce load, unless driver is in crash state
            long messageCount = driver.getMessageCount();
            if (isInCrashState || messageCount % 10 == 0) { // Broadcast every 10th message or crash states
                logger.debug("🌐 Broadcasting driver update: {} at ({}, {}) - {}", 
                    driver.getDriverId(), driver.getCurrentLatitude(), driver.getCurrentLongitude(), driver.getCurrentState());
                messagingTemplate.convertAndSend("/topic/drivers", update);
                
                if (isInCrashState) {
                    logger.info("🌐 Broadcast crash state for {} to web clients", driver.getDriverId());
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to broadcast driver update for {}: {}", driver.getDriverId(), e.getMessage(), e);
        }
    }

    public void broadcastAllDrivers() {
        try {
            var allDrivers = driverManager.getAllDrivers().stream()
                .map(driver -> new DriverLocationUpdate(
                    driver.getDriverId(),
                    driver.getPolicyId(),
                    driver.getVehicleId(),
                    driver.getCurrentLatitude(),
                    driver.getCurrentLongitude(),
                    driver.getCurrentBearing(),
                    driver.getCurrentSpeed(),
                    driver.getCurrentStreet() != null ? driver.getCurrentStreet() : "Unknown Street",
                    driver.getCurrentState(),
                    getRouteDescription(driver),
                    false, // Not a crash event
                    0.0, // No G-force for bulk update
                    Instant.now()
                ))
                .toList();

            logger.info("🌐 Broadcasting {} drivers to all web clients", allDrivers.size());
            messagingTemplate.convertAndSend("/topic/drivers/all", allDrivers);
        } catch (Exception e) {
            logger.error("Failed to broadcast all drivers: {}", e.getMessage(), e);
        }
    }

    private String getRouteDescription(Driver driver) {
        if (driver.getCurrentRoute() == null || driver.getCurrentRoute().isEmpty()) {
            return "No route";
        }
        
        var route = driver.getCurrentRoute();
        var start = route.get(0);
        var end = route.get(route.size() - 1);
        
        return String.format("%s → %s", 
            start.streetName().split(" & ")[0], 
            end.streetName().split(" & ")[0]
        );
    }


}