package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.EnhancedTelematicsMessage;
import com.insurancemegacorp.telematicsgen.model.FlatTelematicsMessage;
import com.insurancemegacorp.telematicsgen.util.TelematicsMessageFlattener;
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
    private final TelematicsMessageFlattener flattener;

    @Value("${telematics.exchange.name:telematics_exchange}")
    private String exchangeName;

    public TelematicsPublisher(RabbitTemplate rabbitTemplate, 
                              WebSocketBroadcastService webSocketService, 
                              TelematicsMessageFlattener flattener) {
        this.rabbitTemplate = rabbitTemplate;
        this.webSocketService = webSocketService;
        this.flattener = flattener;
    }

    public void publishTelematicsData(EnhancedTelematicsMessage message, Driver driver) {
        try {
            // Convert to flat structure for RabbitMQ publishing
            FlatTelematicsMessage flatMessage = flattener.flatten(message);
            
            if (flatMessage == null) {
                logger.warn("Failed to flatten message for vehicle: {}, skipping RabbitMQ publish", message.vehicleId());
                return;
            }
            
            // Publish flattened message to RabbitMQ for optimal downstream processing
            rabbitTemplate.convertAndSend(exchangeName, "", flatMessage);
            
            logger.info("📡 TELEMETRY | {} | VEH:{} | VIN:{} | Street:{} | Speed:{} mph (Limit: {} mph) | G-force:{}g",
                flatMessage.driverId(),
                flatMessage.vehicleId(),
                flatMessage.vin(),
                flatMessage.currentStreet(),
                String.format("%.1f", flatMessage.speedMph()),
                flatMessage.speedLimitMph(),
                String.format("%.2f", flatMessage.gForce()));
                
            // Broadcast to web clients using flat message for consistency
            webSocketService.broadcastDriverUpdate(driver, flatMessage);
                
        } catch (Exception e) {
            logger.error("Failed to publish telematics data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish telematics data", e);
        }
    }
    
    
}