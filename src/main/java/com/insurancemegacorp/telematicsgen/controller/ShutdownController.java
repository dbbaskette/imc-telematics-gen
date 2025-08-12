package com.insurancemegacorp.telematicsgen.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
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
        
        // Select random driver and trigger crash
        var targetDriver = activeDrivers.get((int) (Math.random() * activeDrivers.size()));
        boolean success = driverManager.triggerDemoAccident(targetDriver.getDriverId());

        // If crash was successfully triggered, also publish a crash event
        // TODO: Potential timing race condition - background TelematicsSimulator (500ms intervals) 
        // may publish normal telemetry for same driver around same time as this crash event,
        // potentially confusing crash detection analysis
        if (success) {
            try {
                var crashMessage = dataGenerator.generateCrashEventData(targetDriver);
                publisher.publishTelematicsData(crashMessage, targetDriver);
                logger.info("🚗💥 Demo crash event published via REST API for {}", targetDriver.getDriverId());
            } catch (Exception e) {
                logger.warn("⚠️ Crash event publish failed for {}: {}", targetDriver.getDriverId(), e.getMessage());
            }
        }

        logger.info("🚗💥 REST API crash {} for driver {}", 
                   success ? "triggered" : "failed", targetDriver.getDriverId());
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "driver_id", targetDriver.getDriverId(),
            "message", success ? 
                "Crash triggered for " + targetDriver.getDriverId() : 
                "Failed to trigger crash",
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/trigger-crash/{driverId}")
    public ResponseEntity<Map<String, Object>> triggerSpecificCrash(
            @org.springframework.web.bind.annotation.PathVariable String driverId) {
        logger.info("🚨 REST API crash trigger requested for specific driver: {}", driverId);
        
        boolean success = driverManager.triggerDemoAccident(driverId);
        
        // If crash was successfully triggered, publish crash event to RabbitMQ
        if (success) {
            var crashedDriver = driverManager.getAllDrivers().stream()
                .filter(d -> d.getDriverId().equals(driverId))
                .findFirst()
                .orElse(null);
                
            if (crashedDriver != null) {
                try {
                    var crashMessage = dataGenerator.generateCrashEventData(crashedDriver);
                    publisher.publishTelematicsData(crashMessage, crashedDriver);
                    logger.info("🚨 Manual crash event published to RabbitMQ via REST API for driver {}", driverId);
                } catch (Exception e) {
                    logger.warn("⚠️ Crash event publish failed for {}: {}", driverId, e.getMessage());
                }
            }
        }
        
        logger.info("🚗💥 REST API crash {} for driver {}", 
                   success ? "triggered" : "failed", driverId);
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "driver_id", driverId,
            "message", success ? 
                "Crash triggered for " + driverId : 
                "Failed to trigger crash for " + driverId,
            "timestamp", java.time.Instant.now().toString()
        ));
    }
}