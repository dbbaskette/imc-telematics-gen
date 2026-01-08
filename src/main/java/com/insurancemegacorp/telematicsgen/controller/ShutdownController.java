package com.insurancemegacorp.telematicsgen.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*", maxAge = 3600)
public class ShutdownController {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdown() {
        logger.info("🛑 Shutdown request received from web dashboard");
        
        // Return response immediately
        ResponseEntity<Map<String, String>> response = ResponseEntity.ok(
            Map.of(
                "status", "success",
                "message", "Application shutdown initiated"
            )
        );
        
        // Schedule shutdown in a separate thread to allow response to be sent
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Give time for response to be sent
                logger.info("🛑 Shutting down telematics application...");
                SpringApplication.exit(applicationContext, () -> 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Shutdown interrupted", e);
            }
        }, "shutdown-thread").start();
        
        return response;
    }

    // Optional: Pause/Resume via REST
    @Autowired
    private com.insurancemegacorp.telematicsgen.service.TelematicsSimulator simulator;

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause() {
        simulator.setPaused(true);
        return ResponseEntity.ok(Map.of("status", "ok", "paused", true));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume() {
        simulator.setPaused(false);
        return ResponseEntity.ok(Map.of("status", "ok", "paused", false));
    }

    @PostMapping("/interval")
    public ResponseEntity<Map<String, Object>> setInterval(@org.springframework.web.bind.annotation.RequestParam("ms") long ms) {
        simulator.setIntervalMs(ms);
        return ResponseEntity.ok(Map.of("status", "ok", "intervalMs", simulator.getIntervalMs()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/interval")
    public ResponseEntity<Map<String, Object>> getInterval() {
        return ResponseEntity.ok(Map.of("status", "ok", "interval", simulator.getIntervalMs()));
    }

    @Autowired
    private com.insurancemegacorp.telematicsgen.service.DriverManager driverManager;
    
    @Autowired
    private com.insurancemegacorp.telematicsgen.service.TelematicsDataGenerator dataGenerator;
    
    @Autowired
    private com.insurancemegacorp.telematicsgen.service.TelematicsPublisher publisher;

    @PostMapping("/trigger-crash")
    public ResponseEntity<Map<String, Object>> triggerRandomCrash() {
        logger.info("🚨 REST API crash trigger requested");

        // Get a random active driver (not already in crash state)
        var activeDrivers = driverManager.getAllDrivers().stream()
            .filter(driver -> !driver.getCurrentState().name().contains("CRASH"))
            .toList();

        if (activeDrivers.isEmpty()) {
            logger.warn("⚠️ No active drivers available for crash simulation");
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "No active drivers available",
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        // Select random driver
        var targetDriver = activeDrivers.get((int) (Math.random() * activeDrivers.size()));

        // Don't crash if already in crash state or crashed too recently
        if (targetDriver.getTimeSinceCrashSeconds() < 300) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Driver crashed too recently (5 min cooldown)",
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        // IMPORTANT: Generate crash event data BEFORE recording the crash
        // This captures the speed at impact before it's set to 0
        var crashMessage = dataGenerator.generateCrashEventData(targetDriver);

        // Now record the crash (sets speed to 0 and state to POST_CRASH_IDLE)
        targetDriver.recordCrashEvent(crashMessage.accidentType());

        // Publish the crash event
        try {
            publisher.publishTelematicsData(crashMessage, targetDriver);
            logger.info("🚗💥 Demo crash event published via REST API for {} at {} mph",
                targetDriver.getDriverId(), crashMessage.speedMph());
        } catch (Exception e) {
            logger.warn("⚠️ Crash event publish failed for {}: {}", targetDriver.getDriverId(), e.getMessage());
        }

        logger.info("🚗💥 REST API crash triggered for driver {} - Speed at impact: {} mph, Type: {}",
                   targetDriver.getDriverId(), crashMessage.speedMph(), crashMessage.accidentType());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "driver_id", targetDriver.getDriverId(),
            "speed_at_impact", crashMessage.speedMph(),
            "accident_type", crashMessage.accidentType(),
            "message", "Crash triggered for " + targetDriver.getDriverId(),
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/trigger-crash/{driverId}")
    public ResponseEntity<Map<String, Object>> triggerSpecificCrash(
            @org.springframework.web.bind.annotation.PathVariable String driverId) {
        logger.info("🚨 REST API crash trigger requested for specific driver: {}", driverId);

        int driverIdInt = Integer.parseInt(driverId);

        // Find the driver
        var targetDriver = driverManager.getAllDrivers().stream()
            .filter(d -> d.getDriverId() == driverIdInt)
            .findFirst()
            .orElse(null);

        if (targetDriver == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "driver_id", driverId,
                "message", "Driver not found: " + driverId,
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        // Don't crash if already in crash state
        if (targetDriver.getCurrentState().name().contains("CRASH")) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "driver_id", driverId,
                "message", "Driver already in crash state",
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        // Don't crash too soon after the last crash
        if (targetDriver.getTimeSinceCrashSeconds() < 300) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "driver_id", driverId,
                "message", "Driver crashed too recently (5 min cooldown)",
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        // IMPORTANT: Generate crash event data BEFORE recording the crash
        // This captures the speed at impact before it's set to 0
        var crashMessage = dataGenerator.generateCrashEventData(targetDriver);

        // Now record the crash (sets speed to 0 and state to POST_CRASH_IDLE)
        targetDriver.recordCrashEvent(crashMessage.accidentType());

        // Publish the crash event
        try {
            publisher.publishTelematicsData(crashMessage, targetDriver);
            logger.info("🚨 Manual crash event published to RabbitMQ for driver {} at {} mph",
                driverId, crashMessage.speedMph());
        } catch (Exception e) {
            logger.warn("⚠️ Crash event publish failed for {}: {}", driverId, e.getMessage());
        }

        logger.info("🚗💥 REST API crash triggered for driver {} - Speed at impact: {} mph, Type: {}",
                   driverId, crashMessage.speedMph(), crashMessage.accidentType());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "driver_id", driverId,
            "speed_at_impact", crashMessage.speedMph(),
            "accident_type", crashMessage.accidentType(),
            "message", "Crash triggered for " + driverId,
            "timestamp", java.time.Instant.now().toString()
        ));
    }
}