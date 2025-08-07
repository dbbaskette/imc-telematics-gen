package com.insurancemegacorp.telematicsgen.controller;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
import com.insurancemegacorp.telematicsgen.service.DriverManager;
import com.insurancemegacorp.telematicsgen.service.TelematicsDataGenerator;
import com.insurancemegacorp.telematicsgen.service.TelematicsPublisher;
import com.insurancemegacorp.telematicsgen.service.WebSocketBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    private final WebSocketBroadcastService broadcastService;
    private final DriverManager driverManager;
    private final TelematicsDataGenerator dataGenerator;
    private final TelematicsPublisher publisher;

    public WebSocketController(WebSocketBroadcastService broadcastService, DriverManager driverManager,
                              TelematicsDataGenerator dataGenerator, TelematicsPublisher publisher) {
        this.broadcastService = broadcastService;
        this.driverManager = driverManager;
        this.dataGenerator = dataGenerator;
        this.publisher = publisher;
    }

    @SubscribeMapping("/topic/drivers")
    public void handleDriverSubscription() {
        logger.info("🌐 New client subscribed to driver updates");
        // Send current driver positions to the new subscriber
        broadcastService.broadcastAllDrivers();
    }

    @MessageMapping("/drivers/request")
    @SendTo("/topic/drivers/all")
    public Object requestAllDrivers() {
        logger.info("🌐 Client requested all drivers");
        return driverManager.getAllDrivers().stream()
            .map(driver -> new Object() {
                public final String driver_id = driver.getDriverId();
                public final int policy_id = driver.getPolicyId();
                public final double latitude = driver.getCurrentLatitude();
                public final double longitude = driver.getCurrentLongitude();
                public final double bearing = driver.getCurrentBearing();
                public final double speed_mph = driver.getCurrentSpeed();
                public final String current_street = driver.getCurrentStreet();
                public final String state = driver.getCurrentState().toString();
                public final String route_description = getRouteDescription(driver);
                public final boolean is_crash_event = false;
                public final double g_force = 0.0;
                public final String timestamp = java.time.Instant.now().toString();
            })
            .toList();
    }

    @MessageMapping("/drivers/trigger-accident")
    @SendTo("/topic/drivers/accident")
    public Object triggerRandomAccident() {
        logger.info("🚨 Demo accident trigger requested (random)");
        
        // Get a random active driver (not already in crash state)
        var activeDrivers = driverManager.getAllDrivers().stream()
            .filter(driver -> !driver.getCurrentState().name().contains("CRASH"))
            .toList();
        
        if (activeDrivers.isEmpty()) {
            logger.warn("⚠️ No active drivers available for accident simulation");
            return new Object() {
                public final boolean success = false;
                public final String message = "No active drivers available";
                public final String timestamp = java.time.Instant.now().toString();
            };
        }
        
        // Select random driver and trigger accident
        var targetDriver = activeDrivers.get((int) (Math.random() * activeDrivers.size()));
        boolean success = driverManager.triggerDemoAccident(targetDriver.getDriverId());
        
        logger.info("🚗💥 Demo accident {} for driver {}", 
                   success ? "triggered" : "failed", targetDriver.getDriverId());
        
        final boolean finalSuccess = success;
        final String finalDriverId = targetDriver.getDriverId();
        
        return new Object() {
            public final boolean success = finalSuccess;
            public final String driver_id = finalDriverId;
            public final String message = finalSuccess ? 
                "Accident triggered for " + finalDriverId : 
                "Failed to trigger accident";
            public final String timestamp = java.time.Instant.now().toString();
        };
    }

    @MessageMapping("/drivers/trigger-accident-specific")
    @SendTo("/topic/drivers/accident")
    public Object triggerSpecificAccident(String driverId) {
        logger.info("🚨 Demo accident trigger requested for specific driver: {}", driverId);
        
        boolean success = driverManager.triggerDemoAccident(driverId);
        
        // If crash was successfully triggered, publish crash event to RabbitMQ
        if (success) {
            Driver crashedDriver = driverManager.getAllDrivers().stream()
                .filter(d -> d.getDriverId().equals(driverId))
                .findFirst()
                .orElse(null);
                
            if (crashedDriver != null) {
                EnhancedTelematicsMessage crashMessage = dataGenerator.generateCrashEventData(crashedDriver);
                publisher.publishTelematicsData(crashMessage, crashedDriver);
                logger.info("🚨 Manual crash event published to RabbitMQ for driver {}", driverId);
            }
        }
        
        logger.info("🚗💥 Demo accident {} for driver {}", 
                   success ? "triggered" : "failed", driverId);
        
        final boolean finalSuccess = success;
        final String finalDriverId = driverId;
        
        return new Object() {
            public final boolean success = finalSuccess;
            public final String driver_id = finalDriverId;
            public final String message = finalSuccess ? 
                "Accident triggered for " + finalDriverId : 
                "Failed to trigger accident for " + finalDriverId;
            public final String timestamp = java.time.Instant.now().toString();
        };
    }

    // --- Simulation runtime controls ---
    @MessageMapping("/sim/toggle-pause")
    @SendTo("/topic/sim/status")
    public Object togglePause() {
        // Delegate to simulator via publisher/driverManager path; expose through DriverManager for now
        // In absence of DI here, call directly via application context is avoided; use a static holder if needed.
        // For simplicity, use DriverManager to access simulator via broadcast service dependency path is not available.
        // Instead, publish a status-only event; REST endpoint handles actual toggle.
        return new Object() {
            public final boolean accepted = true;
            public final String message = "Toggle requested";
        };
    }

    private String getRouteDescription(com.insurancemegacorp.telematicsgen.model.Driver driver) {
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