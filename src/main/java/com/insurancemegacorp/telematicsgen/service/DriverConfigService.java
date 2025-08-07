package com.insurancemegacorp.telematicsgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemegacorp.telematicsgen.model.DriverConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for loading and managing driver configurations from file-based storage.
 * Loads driver information including VIN, policy numbers, and vehicle details.
 */
@Service
public class DriverConfigService {

    private static final Logger logger = LoggerFactory.getLogger(DriverConfigService.class);
    private final ObjectMapper objectMapper;
    private List<DriverConfig> driverConfigs;

    public DriverConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadDriverConfigurations();
    }

    /**
     * Load driver configurations from the drivers.json file.
     */
    private void loadDriverConfigurations() {
        try {
            ClassPathResource resource = new ClassPathResource("drivers.json");
            
            if (!resource.exists()) {
                logger.error("❌ Driver configuration file 'drivers.json' not found in classpath");
                this.driverConfigs = new ArrayList<>();
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);
                JsonNode driversNode = rootNode.get("drivers");
                
                if (driversNode == null || !driversNode.isArray()) {
                    logger.error("❌ Invalid driver configuration format: 'drivers' array not found");
                    this.driverConfigs = new ArrayList<>();
                    return;
                }

                List<DriverConfig> configs = new ArrayList<>();
                for (JsonNode driverNode : driversNode) {
                    try {
                        DriverConfig config = objectMapper.treeToValue(driverNode, DriverConfig.class);
                        configs.add(config);
                        logger.debug("✅ Loaded driver config: {} - {} ({})", 
                            config.getDriverId(), config.driverName(), config.vin());
                    } catch (Exception e) {
                        logger.error("❌ Failed to parse driver configuration: {}", driverNode, e);
                    }
                }

                this.driverConfigs = configs;
                logger.info("✅ Successfully loaded {} driver configurations from file", configs.size());
                
                // Log summary of loaded drivers
                configs.forEach(config -> 
                    logger.info("🚗 Driver {}: {} - {} {} (Policy: {}, VIN: {})", 
                        config.driverId(),
                        config.driverName(),
                        config.vehicleYear(),
                        config.getVehicleDescription(),
                        config.policyId(),
                        config.vin())
                );

            }
        } catch (IOException e) {
            logger.error("❌ Failed to load driver configurations from file", e);
            this.driverConfigs = new ArrayList<>();
        }
    }

    /**
     * Get all loaded driver configurations.
     */
    public List<DriverConfig> getAllDriverConfigs() {
        return List.copyOf(driverConfigs);
    }

    /**
     * Get a specific driver configuration by driver number.
     */
    public DriverConfig getDriverConfigByNumber(int driverNumber) {
        return driverConfigs.stream()
            .filter(config -> config.driverId() == driverNumber)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get a driver configuration by policy number.
     */
    public DriverConfig getDriverConfigByPolicyNumber(int policyId) {
        return driverConfigs.stream()
            .filter(config -> config.policyId() == policyId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get a driver configuration by VIN.
     */
    public DriverConfig getDriverConfigByVin(String vin) {
        return driverConfigs.stream()
            .filter(config -> config.vin().equals(vin))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the total number of configured drivers.
     */
    public int getDriverCount() {
        return driverConfigs.size();
    }

    /**
     * Check if driver configurations are loaded and available.
     */
    public boolean hasDriverConfigs() {
        return !driverConfigs.isEmpty();
    }

    /**
     * Reload driver configurations from file (useful for runtime updates).
     */
    public void reloadConfigurations() {
        logger.info("🔄 Reloading driver configurations from file...");
        loadDriverConfigurations();
    }
}
