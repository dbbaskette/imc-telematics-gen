package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TelematicsSimulator {

    private static final Logger logger = LoggerFactory.getLogger(TelematicsSimulator.class);

    private final TelematicsDataGenerator dataGenerator;
    private final TelematicsPublisher publisher;
    private final DriverManager driverManager;
    private final WebSocketBroadcastService webSocketService;
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong totalMessageCount = new AtomicLong(0);

    @Value("${telematics.simulation.interval-ms:500}")
    private long intervalMs;

    @Value("${telematics.policy.id:ACME-AUTO-98765}")
    private String basePolicyId;

    @Value("${telematics.location.latitude:40.7128}")
    private double baseLatitude;

    @Value("${telematics.location.longitude:-74.0060}")
    private double baseLongitude;

    private volatile boolean running = false;
    private volatile boolean paused = false;

    public TelematicsSimulator(TelematicsDataGenerator dataGenerator, 
                             TelematicsPublisher publisher,
                             DriverManager driverManager,
                             WebSocketBroadcastService webSocketService) {
        this.dataGenerator = dataGenerator;
        this.publisher = publisher;
        this.driverManager = driverManager;
        this.webSocketService = webSocketService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async("taskExecutor")
    public void startSimulation() {
        logger.info("🚗 Starting multi-driver telematics simulation...");
        
        // Initialize drivers
        driverManager.initializeDrivers(basePolicyId, baseLatitude, baseLongitude);
        
        logger.info("📡 Sending data every {}ms from {} drivers", 
            intervalMs, driverManager.getDriverCount());
        logger.info("💥 Crash events will occur periodically with post-crash idle periods");
        logger.info("Press Ctrl+C to stop.");
        
        // Broadcast initial driver positions to any connected web clients
        logger.info("🌐 Broadcasting initial driver positions to web clients");
        webSocketService.broadcastAllDrivers();
        
        running = true;
        
        while (running) {
            try {
                // If paused, wait briefly and continue loop without generating
                if (paused) {
                    Thread.sleep(300);
                    continue;
                }
                // Select a driver for this message
                Driver selectedDriver = driverManager.selectDriverForMessage();
                
                // Update driver behavior and state
                driverManager.updateDriverBehavior(selectedDriver);
                
                EnhancedTelematicsMessage message;
                
                // Check if this driver should have a crash event
                if (selectedDriver.getCurrentState().name().equals("DRIVING") && 
                    shouldSimulateCrash(selectedDriver)) {
                    
                    logger.warn("💥💥💥 CRASH EVENT - Driver {}! 💥💥💥", selectedDriver.getDriverId());
                    message = dataGenerator.generateCrashEventData(selectedDriver);
                    selectedDriver.recordCrashEvent();
                } else {
                    // Generate normal telemetry based on driver state
                    message = dataGenerator.generateTelematicsData(selectedDriver);
                }
                
                selectedDriver.incrementMessageCount();
                publisher.publishTelematicsData(message, selectedDriver);
                totalMessageCount.incrementAndGet();
                
                // Log driver states periodically
                if (totalMessageCount.get() % 50 == 0) {
                    driverManager.logDriverStates();
                }
                
                // Random sleep interval to simulate real-world variance
                long sleepTime = intervalMs + random.nextInt(1000) - 500; // ±500ms variance
                Thread.sleep(Math.max(sleepTime, 500)); // Minimum 500ms
                
            } catch (InterruptedException e) {
                logger.info("🛑 Simulation interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("❌ Error in simulation: {}", e.getMessage(), e);
                try {
                    Thread.sleep(5000); // Wait 5 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("🛑 Multi-driver telematics simulation stopped. Total messages sent: {}", 
            totalMessageCount.get());
        logFinalDriverStats();
    }

    private boolean shouldSimulateCrash(Driver driver) {
        // Use the driver manager's logic for crash determination
        return false; // Driver manager handles this in updateDriverBehavior
    }

    private void logFinalDriverStats() {
        logger.info("📊 Final Driver Statistics:");
        driverManager.getAllDrivers().forEach(driver -> 
            logger.info("  {} - Messages: {}, Final State: {}, Crashes: {}", 
                driver.getDriverId(),
                driver.getMessageCount(),
                driver.getCurrentState(),
                driver.getLastCrashTime() != null ? "Yes" : "No")
        );
    }

    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void stopSimulation() {
        if (running) {
            logger.info("🛑 Graceful shutdown initiated...");
            running = false;
            
            // Give the simulation loop time to finish current iteration
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void forceStop() {
        running = false;
    }
    
    public long getTotalMessageCount() {
        return totalMessageCount.get();
    }

    // --- Runtime controls ---
    public void setPaused(boolean paused) {
        this.paused = paused;
        logger.info(paused ? "⏸️ Simulation paused" : "▶️ Simulation resumed");
    }

    public boolean isPaused() {
        return paused;
    }

    public void togglePause() {
        setPaused(!paused);
    }

    public void setIntervalMs(long intervalMs) {
        if (intervalMs < 100) {
            intervalMs = 100; // Safety lower bound
        }
        this.intervalMs = intervalMs;
        logger.info("⏱️ Simulation interval set to {} ms", this.intervalMs);
    }

    public long getIntervalMs() {
        return intervalMs;
    }
}