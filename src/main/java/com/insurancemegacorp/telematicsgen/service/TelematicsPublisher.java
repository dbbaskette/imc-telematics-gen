package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.TelematicsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelematicsPublisher {

    private static final Logger logger = LoggerFactory.getLogger(TelematicsPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final WebSocketBroadcastService webSocketService;

    @Value("${telematics.queue.name:telematics_stream}")
    private String queueName;

    public TelematicsPublisher(RabbitTemplate rabbitTemplate, WebSocketBroadcastService webSocketService) {
        this.rabbitTemplate = rabbitTemplate;
        this.webSocketService = webSocketService;
    }

    public void publishTelematicsData(TelematicsMessage message, Driver driver) {
        try {
            rabbitTemplate.convertAndSend(queueName, message);
            
            double accelX = message.sensors().accelerometer().x();
            String driverId = extractDriverId(message.policyId());
            
            // Just log the raw sensor data - no crash detection logic
            logger.info("📡 {} | {} | Speed: {} mph | G-force: {}g", 
                driverId, message.policyId(), message.speedMph(), 
                String.format("%.2f", accelX));
                
            // Broadcast to web clients (let the frontend/dashboard handle crash detection if needed)
            webSocketService.broadcastDriverUpdate(driver, message);
                
        } catch (Exception e) {
            logger.error("Failed to publish telematics data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish telematics data", e);
        }
    }
    
    // Legacy method for backward compatibility
    public void publishTelematicsData(TelematicsMessage message) {
        try {
            rabbitTemplate.convertAndSend(queueName, message);
            
            double accelX = message.sensors().accelerometer().x();
            String driverId = extractDriverId(message.policyId());
            logger.info("📡 {} | {} | Speed: {} mph | G-force: {}g", 
                driverId, message.policyId(), message.speedMph(), 
                String.format("%.2f", accelX));
                
        } catch (Exception e) {
            logger.error("Failed to publish telematics data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish telematics data", e);
        }
    }
    
    private String extractDriverId(String policyId) {
        if (policyId.contains("DRIVER-")) {
            return policyId.substring(policyId.lastIndexOf("DRIVER-"));
        }
        return "UNKNOWN";
    }
}