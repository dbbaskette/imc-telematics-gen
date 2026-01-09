package com.insurancemegacorp.telematicsgen.controller;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverConfig;
import com.insurancemegacorp.telematicsgen.model.FlatTelematicsMessage;
import com.insurancemegacorp.telematicsgen.service.DriverConfigService;
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
    private final DriverConfigService driverConfigService;
    private final TelematicsDataGenerator dataGenerator;
    private final TelematicsPublisher publisher;

    public WebSocketController(WebSocketBroadcastService broadcastService, DriverManager driverManager,
                              DriverConfigService driverConfigService,
                              TelematicsDataGenerator dataGenerator, TelematicsPublisher publisher) {
        this.broadcastService = broadcastService;
        this.driverManager = driverManager;
        this.driverConfigService = driverConfigService;
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
                public final int driver_id = driver.getDriverId();
                public final int policy_id = driver.getPolicyId();
                public final double latitude = driver.getCurrentLatitude();
                public final double longitude = driver.getCurrentLongitude();
                public final double bearing = driver.getCurrentBearing();
                public final double speed_mph = driver.getCurrentSpeed();
                public final String current_street = driver.getCurrentStreet();
                public final String state = driver.getCurrentState().toString();
                public final String route_description = driverManager.getRouteDescription(driver);
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
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("message", "No active drivers available");
                put("timestamp", java.time.Instant.now().toString());
            }};
        }

        // Select random driver
        var targetDriver = activeDrivers.get((int) (Math.random() * activeDrivers.size()));

        // Generate crash data BEFORE triggering (to capture speed at impact)
        FlatTelematicsMessage crashMessage = dataGenerator.generateCrashEventData(targetDriver);

        // Now trigger the accident (sets speed to 0)
        boolean success = driverManager.triggerDemoAccident(targetDriver.getDriverId());

        // If crash was successfully triggered, publish crash event to RabbitMQ
        if (success) {
            try {
                publisher.publishTelematicsData(crashMessage, targetDriver);
                logger.info("🚗💥 Demo crash event published for {}", targetDriver.getDriverId());
            } catch (Exception e) {
                logger.warn("⚠️ Crash event publish failed for {}: {}", targetDriver.getDriverId(), e.getMessage());
            }
        }

        logger.info("🚗💥 Demo accident {} for driver {}",
                   success ? "triggered" : "failed", targetDriver.getDriverId());

        // Return comprehensive crash details for popup
        var result = new java.util.HashMap<String, Object>();
        result.put("success", success);
        result.put("driver_id", targetDriver.getDriverId());
        result.put("driver_name", getDriverName(targetDriver));
        result.put("vehicle", targetDriver.getVin());
        result.put("accident_type", crashMessage.accidentType());
        result.put("speed_at_impact", crashMessage.speedMph());
        result.put("speed_limit", crashMessage.speedLimitMph());
        result.put("street", crashMessage.currentStreet());
        result.put("g_force", crashMessage.gForce());
        result.put("latitude", crashMessage.gpsLatitude());
        result.put("longitude", crashMessage.gpsLongitude());
        result.put("message", success ? "Accident triggered for " + targetDriver.getDriverId() : "Failed to trigger accident");
        result.put("timestamp", java.time.Instant.now().toString());
        return result;
    }

    @MessageMapping("/drivers/trigger-accident-specific")
    @SendTo("/topic/drivers/accident")
    public Object triggerSpecificAccident(String driverId) {
        logger.info("🚨 Demo accident trigger requested for specific driver: {}", driverId);

        int driverIdInt = Integer.parseInt(driverId);

        // Find the driver first
        Driver targetDriver = driverManager.getAllDrivers().stream()
            .filter(d -> d.getDriverId() == driverIdInt)
            .findFirst()
            .orElse(null);

        if (targetDriver == null) {
            var result = new java.util.HashMap<String, Object>();
            result.put("success", false);
            result.put("driver_id", driverIdInt);
            result.put("message", "Driver not found: " + driverId);
            result.put("timestamp", java.time.Instant.now().toString());
            return result;
        }

        // Generate crash data BEFORE triggering (to capture speed at impact)
        FlatTelematicsMessage crashMessage = dataGenerator.generateCrashEventData(targetDriver);

        // Now trigger the accident (sets speed to 0)
        boolean success = driverManager.triggerDemoAccident(driverIdInt);

        // If crash was successfully triggered, publish crash event to RabbitMQ
        if (success) {
            try {
                publisher.publishTelematicsData(crashMessage, targetDriver);
                logger.info("🚨 Manual crash event published to RabbitMQ for driver {}", driverId);
            } catch (Exception e) {
                logger.warn("⚠️ Crash event publish failed for {}: {}", driverId, e.getMessage());
            }
        }

        logger.info("🚗💥 Demo accident {} for driver {}",
                   success ? "triggered" : "failed", driverId);

        // Return comprehensive crash details for popup
        var result = new java.util.HashMap<String, Object>();
        result.put("success", success);
        result.put("driver_id", targetDriver.getDriverId());
        result.put("driver_name", getDriverName(targetDriver));
        result.put("vehicle", targetDriver.getVin());
        result.put("accident_type", crashMessage.accidentType());
        result.put("speed_at_impact", crashMessage.speedMph());
        result.put("speed_limit", crashMessage.speedLimitMph());
        result.put("street", crashMessage.currentStreet());
        result.put("g_force", crashMessage.gForce());
        result.put("latitude", crashMessage.gpsLatitude());
        result.put("longitude", crashMessage.gpsLongitude());
        result.put("message", success ? "Accident triggered for " + targetDriver.getDriverId() : "Failed to trigger accident for " + driverId);
        result.put("timestamp", java.time.Instant.now().toString());
        return result;
    }

    @MessageMapping("/sim/toggle-random-accidents")
    @SendTo("/topic/sim/random-accidents")
    public Object toggleRandomAccidents(String enabled) {
        boolean isEnabled = Boolean.parseBoolean(enabled);
        driverManager.setRandomAccidentsEnabled(isEnabled);
        logger.info("🎲 Random accidents {} via WebSocket", isEnabled ? "enabled" : "disabled");
        
        return new Object() {
            public final boolean enabled = isEnabled;
            public final String message = "Random accidents " + (isEnabled ? "enabled" : "disabled");
            public final String timestamp = java.time.Instant.now().toString();
        };
    }

    private String getDriverName(Driver driver) {
        return driverConfigService.getAllDriverConfigs().stream()
            .filter(config -> config.getDriverId() == driver.getDriverId())
            .findFirst()
            .map(DriverConfig::driverName)
            .orElse("Driver " + driver.getDriverId());
    }
}