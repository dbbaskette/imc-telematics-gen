package com.insurancemegacorp.telematicsgen.util;

import com.insurancemegacorp.telematicsgen.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * High-performance utility for converting EnhancedTelematicsMessage to FlatTelematicsMessage.
 * Optimized for minimal object allocation and fast transformation.
 */
@Component
public class TelematicsMessageFlattener {
    
    private static final Logger logger = LoggerFactory.getLogger(TelematicsMessageFlattener.class);

    /**
     * Converts an EnhancedTelematicsMessage to a FlatTelematicsMessage.
     * This method is optimized for performance and handles null safety.
     * 
     * @param enhanced the enhanced message to flatten
     * @return flattened message, or null if conversion fails
     */
    public FlatTelematicsMessage flatten(EnhancedTelematicsMessage enhanced) {
        if (enhanced == null) {
            logger.warn("Cannot flatten null EnhancedTelematicsMessage");
            return null;
        }
        
        try {
            // Extract nested sensor data with null safety
            EnhancedSensorData sensors = enhanced.sensors();
            if (sensors == null) {
                logger.error("EnhancedTelematicsMessage has null sensors data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Extract GPS data
            EnhancedGpsData gps = sensors.gps();
            if (gps == null) {
                logger.error("EnhancedSensorData has null GPS data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Extract accelerometer data
            AccelerometerData accelerometer = sensors.accelerometer();
            if (accelerometer == null) {
                logger.error("EnhancedSensorData has null accelerometer data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Extract gyroscope data
            GyroscopeData gyroscope = sensors.gyroscope();
            if (gyroscope == null) {
                logger.error("EnhancedSensorData has null gyroscope data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Extract magnetometer data
            MagnetometerData magnetometer = sensors.magnetometer();
            if (magnetometer == null) {
                logger.error("EnhancedSensorData has null magnetometer data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Extract device metadata
            DeviceMetadata device = sensors.device();
            if (device == null) {
                logger.error("EnhancedSensorData has null device data, skipping message for vehicle: {}", 
                    enhanced.vehicleId());
                return null;
            }
            
            // Create flattened message with all fields
            return new FlatTelematicsMessage(
                // Core message fields
                enhanced.policyId(),
                enhanced.vehicleId(),
                enhanced.vin(),
                enhanced.eventTime(),
                enhanced.speedMph(),
                enhanced.speedLimitMph(),
                enhanced.gForce(),
                enhanced.driverId(),
                enhanced.currentStreet(),
                
                // GPS data fields
                gps.latitude(),
                gps.longitude(),
                gps.altitude(),
                gps.speed(),
                gps.bearing(),
                gps.accuracy(),
                gps.satelliteCount(),
                gps.fixTime(),
                
                // Accelerometer data fields
                accelerometer.x(),
                accelerometer.y(),
                accelerometer.z(),
                
                // Gyroscope data fields (keeping x/y/z naming)
                gyroscope.x(),
                gyroscope.y(),
                gyroscope.z(),
                
                // Magnetometer data fields
                magnetometer.x(),
                magnetometer.y(),
                magnetometer.z(),
                magnetometer.heading(),
                
                // Environmental data
                sensors.barometricPressure(),
                
                // Device metadata fields
                device.batteryLevel(),
                device.signalStrength(),
                device.orientation(),
                device.screenOn(),
                device.charging()
            );
            
        } catch (Exception e) {
            logger.error("Failed to flatten EnhancedTelematicsMessage for vehicle: {}, error: {}", 
                enhanced.vehicleId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Batch conversion method for processing multiple messages efficiently.
     * 
     * @param enhancedMessages array of enhanced messages to flatten
     * @return array of flattened messages (null entries where conversion failed)
     */
    public FlatTelematicsMessage[] flattenBatch(EnhancedTelematicsMessage[] enhancedMessages) {
        if (enhancedMessages == null || enhancedMessages.length == 0) {
            return new FlatTelematicsMessage[0];
        }
        
        FlatTelematicsMessage[] result = new FlatTelematicsMessage[enhancedMessages.length];
        
        for (int i = 0; i < enhancedMessages.length; i++) {
            result[i] = flatten(enhancedMessages[i]);
        }
        
        return result;
    }
}
