package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.FlatTelematicsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TelematicsPublisher {

    private static final Logger logger = LoggerFactory.getLogger(TelematicsPublisher.class);
    private static final int MAX_RETRIES = 3;
    private static final long CONFIRM_TIMEOUT_MS = 5000;

    private final RabbitTemplate rabbitTemplate;
    private final WebSocketBroadcastService webSocketService;
    private final Counter messagesSentCounter;
    private final Counter messagesFailedCounter;
    private final Counter messagesRetriedCounter;
    private final TelematicsRateService rateService;

    @Value("${telematics.exchange.name:telematics_exchange}")
    private String exchangeName;

    public TelematicsPublisher(RabbitTemplate rabbitTemplate,
                              WebSocketBroadcastService webSocketService,
                              MeterRegistry meterRegistry,
                              TelematicsRateService rateService) {
        this.rabbitTemplate = rabbitTemplate;
        this.webSocketService = webSocketService;
        this.rateService = rateService;
        this.messagesSentCounter = Counter.builder("telematics.messages.sent")
            .description("Total number of telematics messages sent to RabbitMQ")
            .register(meterRegistry);
        this.messagesFailedCounter = Counter.builder("telematics.messages.failed")
            .description("Total number of telematics messages that failed to send")
            .register(meterRegistry);
        this.messagesRetriedCounter = Counter.builder("telematics.messages.retried")
            .description("Total number of telematics message retry attempts")
            .register(meterRegistry);
    }

    @PostConstruct
    public void setupConfirmCallback() {
        // Set up returns callback for unroutable messages (only if not already set)
        try {
            rabbitTemplate.setReturnsCallback(returned -> {
                logger.error("❌ Message returned (unroutable): exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
            });
        } catch (IllegalStateException e) {
            logger.debug("Returns callback already configured");
        }

        logger.info("✅ RabbitMQ publisher confirms enabled");
    }

    public void publishTelematicsData(FlatTelematicsMessage message, Driver driver) {
        boolean isCrashEvent = message.gForce() >= 2.5; // Treat high G-force as crash

        if (isCrashEvent) {
            // For crash events, use confirmed publish with retry
            publishWithConfirmation(message, driver);
        } else {
            // For normal telemetry, use standard publish (fire-and-forget is OK)
            publishStandard(message, driver);
        }
    }

    /**
     * Publish with confirmation and retry logic - used for critical messages like crash events.
     */
    private void publishWithConfirmation(FlatTelematicsMessage message, Driver driver) {
        int attempt = 0;
        boolean confirmed = false;

        while (attempt < MAX_RETRIES && !confirmed) {
            attempt++;
            String correlationId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(correlationId);

            try {
                // Send with correlation data for confirmation tracking
                rabbitTemplate.convertAndSend(exchangeName, "", message, correlationData);

                // Wait for confirmation
                CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (confirm != null && confirm.isAck()) {
                    confirmed = true;
                    messagesSentCounter.increment();
                    rateService.incrementMessageCount();

                    logger.info("🚨📡 CRASH EVENT CONFIRMED | Driver:{} | Speed:{} mph | SpeedLimit:{} mph | G-force:{}g | Street:{} | Type:{} | correlationId={}",
                        message.driverId(),
                        String.format("%.2f", message.speedMph()),
                        message.speedLimitMph(),
                        String.format("%.2f", message.gForce()),
                        message.currentStreet(),
                        message.accidentType(),
                        correlationId);
                } else {
                    logger.warn("⚠️ Crash event not acknowledged, attempt {}/{}: driver={}, reason={}",
                        attempt, MAX_RETRIES, message.driverId(),
                        confirm != null ? confirm.getReason() : "timeout");
                    messagesRetriedCounter.increment();
                }

            } catch (Exception e) {
                logger.warn("⚠️ Crash event publish failed, attempt {}/{}: driver={}, error={}",
                    attempt, MAX_RETRIES, message.driverId(), e.getMessage());
                messagesRetriedCounter.increment();

                // Brief delay before retry
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(100 * attempt); // Exponential backoff: 100ms, 200ms, 300ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!confirmed) {
            messagesFailedCounter.increment();
            logger.error("❌ CRASH EVENT LOST after {} attempts: driver={}, G-force={}",
                MAX_RETRIES, message.driverId(), message.gForce());
        }

        // Broadcast to web clients
        webSocketService.broadcastDriverUpdate(driver, message);
    }

    /**
     * Standard publish without confirmation - used for normal telemetry.
     */
    private void publishStandard(FlatTelematicsMessage message, Driver driver) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, "", message);
            messagesSentCounter.increment();
            rateService.incrementMessageCount();

            logger.debug("📡 TELEMETRY | {} | VEH:{} | VIN:{} | Street:{} | Speed:{} mph (Limit: {} mph) | G-force:{}g",
                message.driverId(),
                message.vehicleId(),
                message.vin(),
                message.currentStreet(),
                String.format("%.1f", message.speedMph()),
                message.speedLimitMph(),
                String.format("%.2f", message.gForce()));

            // Broadcast to web clients
            webSocketService.broadcastDriverUpdate(driver, message);

        } catch (Exception e) {
            logger.error("Failed to publish telematics data: {}", e.getMessage(), e);
            messagesFailedCounter.increment();
            throw new RuntimeException("Failed to publish telematics data", e);
        }
    }
}
