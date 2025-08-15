package com.insurancemegacorp.telematicsgen.service;

import com.insurancemegacorp.telematicsgen.model.Driver;
import com.insurancemegacorp.telematicsgen.model.DriverState;
import com.insurancemegacorp.telematicsgen.model.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;

class DriverManagerTest {

    private DriverManager driverManager;
    private FileBasedRouteService mockRouteService;
    private DestinationRouteService mockDestinationRouteService;

    @BeforeEach
    void setUp() {
        mockRouteService = mock(FileBasedRouteService.class);
        mockDestinationRouteService = mock(DestinationRouteService.class);
        
        // Create mock route data with multiple waypoints for proper testing
        List<RoutePoint> mockRoute = List.of(
            new RoutePoint(33.7490, -84.3880, "Test Street Start", 35, false, "none"),
            new RoutePoint(33.7495, -84.3885, "Test Street Middle", 35, false, "none"),
            new RoutePoint(33.7500, -84.3890, "Test Street End", 35, false, "none"),
            new RoutePoint(33.7505, -84.3895, "Test Street Further", 35, false, "none"),
            new RoutePoint(33.7510, -84.3900, "Test Street Final", 35, false, "none")
        );
        
        when(mockRouteService.getRandomRoute()).thenReturn(mockRoute);
        when(mockRouteService.getRouteByName(anyString())).thenReturn(mockRoute);
        
        // Mock destination service for behavior updates
        when(mockDestinationRouteService.generateRandomDestination()).thenReturn(
            new com.insurancemegacorp.telematicsgen.model.Destination(33.7500, -84.3890, "Test Destination", "test", 5.0)
        );
        when(mockDestinationRouteService.generateRouteToDestination(anyDouble(), anyDouble(), any())).thenReturn(mockRoute);
        
        DriverConfigService mockDriverConfigService = mock(DriverConfigService.class);
        List<com.insurancemegacorp.telematicsgen.model.DriverConfig> mockDriverConfigs = List.of(
            new com.insurancemegacorp.telematicsgen.model.DriverConfig(400001, 200001, "VIN-001", 300001, "John Doe", "Honda", "Civic", 2023, "GA-1001", 33.7490, -84.3880, "peachtree_south"),
            new com.insurancemegacorp.telematicsgen.model.DriverConfig(400002, 200002, "VIN-002", 300002, "Jane Smith", "Toyota", "Camry", 2022, "GA-1002", 33.7500, -84.3890, "downtown_connector")
        );
        when(mockDriverConfigService.getAllDriverConfigs()).thenReturn(mockDriverConfigs);

        DailyRoutineService mockDailyRoutineService = mock(DailyRoutineService.class);
        driverManager = new DriverManager(mockRouteService, mockDestinationRouteService, mockDriverConfigService, mockDailyRoutineService);
        ReflectionTestUtils.setField(driverManager, "driverCount", 2);
        ReflectionTestUtils.setField(driverManager, "crashFrequency", 10);
        ReflectionTestUtils.setField(driverManager, "postCrashIdleMinutes", 1);
        ReflectionTestUtils.setField(driverManager, "randomStopProbability", 0.1);
        ReflectionTestUtils.setField(driverManager, "breakDurationMinutes", 1);
        
        // Set time-based behavior fields
        ReflectionTestUtils.setField(driverManager, "nightStartHour", 20);
        ReflectionTestUtils.setField(driverManager, "nightEndHour", 6);
        ReflectionTestUtils.setField(driverManager, "nightDrivingReduction", 0.7);
        ReflectionTestUtils.setField(driverManager, "nightParkedProbability", 0.85);
        ReflectionTestUtils.setField(driverManager, "peakHours", List.of(7, 8, 17, 18));
        ReflectionTestUtils.setField(driverManager, "peakDrivingBoost", 1.5);
    }

    @Test
    void initializeDrivers_shouldCreateCorrectNumberOfDrivers() {
        driverManager.initializeDrivers("TEST-POLICY", 33.7490, -84.3880);
        
        assertThat(driverManager.getDriverCount()).isEqualTo(2);
        assertThat(driverManager.getAllDrivers()).hasSize(2);
        
        assertThat(driverManager.getAllDrivers().get(0).getDriverId()).isEqualTo(400001);
        assertThat(driverManager.getAllDrivers().get(1).getDriverId()).isEqualTo(400002);
        
        // Verify drivers are positioned near route waypoints (with GPS variation)
        assertThat(driverManager.getAllDrivers().get(0).getCurrentLatitude()).isCloseTo(33.7490, within(0.01));
        assertThat(driverManager.getAllDrivers().get(0).getCurrentLongitude()).isCloseTo(-84.3880, within(0.01));
    }

    @Test
    void selectDriverForMessage_shouldReturnDriver() {
        driverManager.initializeDrivers("TEST-POLICY", 33.7490, -84.3880);
        
        Driver selectedDriver = driverManager.selectDriverForMessage();
        
        assertThat(selectedDriver).isNotNull();
        assertThat(selectedDriver.getDriverId()).isBetween(400001, 400017);
    }

    @Test
    void selectDriverForMessage_shouldThrowWhenNoDriversInitialized() {
        assertThrows(IllegalStateException.class, () -> 
            driverManager.selectDriverForMessage());
    }

    @Test
    void updateDriverBehavior_shouldHandleDriverStateChanges() {
        driverManager.initializeDrivers("TEST-POLICY", 33.7490, -84.3880);
        Driver driver = driverManager.getAllDrivers().get(0);
        
        // Initial state should be any valid driving state
        assertThat(driver.getCurrentState()).isIn(DriverState.PARKED, DriverState.DRIVING, DriverState.TRAFFIC_STOP, DriverState.BREAK_TIME);
        
        // Update behavior multiple times to potentially trigger state changes
        for (int i = 0; i < 10; i++) {
            driverManager.updateDriverBehavior(driver);
        }
        
        // Driver should still be in a valid state
        assertThat(driver.getCurrentState()).isIn(
            DriverState.PARKED, 
            DriverState.DRIVING, 
            DriverState.TRAFFIC_STOP,
            DriverState.BREAK_TIME,
            DriverState.POST_CRASH_IDLE
        );
    }

    @Test
    void driver_shouldTrackStateChanges() {
        Driver driver = new Driver(999001, 200001, 300001, "1HGBH41JXMN109001", 33.7490, -84.3880);
        
        assertThat(driver.getCurrentState()).isEqualTo(DriverState.PARKED);
        assertThat(driver.getCurrentSpeed()).isEqualTo(0.0);
        
        driver.setCurrentState(DriverState.DRIVING);
        driver.setCurrentSpeed(30.0);
        
        assertThat(driver.getCurrentState()).isEqualTo(DriverState.DRIVING);
        assertThat(driver.getCurrentSpeed()).isEqualTo(30.0);
        assertThat(driver.isStationary()).isFalse();
    }

    @Test
    void driver_shouldHandleCrashEvents() {
        Driver driver = new Driver(999001, 200001, 300001, "1HGBH41JXMN109001", 33.7490, -84.3880);
        driver.setCurrentState(DriverState.DRIVING);
        driver.setCurrentSpeed(35.0);
        
        driver.recordCrashEvent();
        
        assertThat(driver.getCurrentState()).isEqualTo(DriverState.POST_CRASH_IDLE);
        assertThat(driver.getCurrentSpeed()).isEqualTo(0.0);
        assertThat(driver.getLastCrashTime()).isNotNull();
        assertThat(driver.isStationary()).isTrue();
    }
}