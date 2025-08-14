package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
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

    @Value("${telematics.exchange.name:telematics_exchange}")
    private String exchangeName;

    public TelematicsPublisher(RabbitTemplate rabbitTemplate, WebSocketBroadcastService webSocketService) {
        this.rabbitTemplate = rabbitTemplate;
        this.webSocketService = webSocketService;
    }

    public void publishTelematicsData(EnhancedTelematicsMessage message, Driver driver) {
        try {
            // Publish to an exchange for fan-out pattern
            rabbitTemplate.convertAndSend(exchangeName, "", message);
            
            // Enhanced logging with street information and VIN - using pre-calculated G-force
            logger.info("📡 {} | policy:{} | VEH:{} | VIN:{} | Street:{} | Speed:{} mph | G-force:{}g", 
                driver.getDriverId(), message.policyId(), message.vehicleId(), message.vin(), message.currentStreet(), 
                message.speedMph(), String.format("%.2f", message.gForce()));
                
            // Broadcast to web clients (let the frontend/dashboard handle crash detection if needed)
            webSocketService.broadcastDriverUpdate(driver, message);
                
        } catch (Exception e) {
            logger.error("Failed to publish telematics data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish telematics data", e);
        }
    }
    
    
}