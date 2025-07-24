package com.acme.insurance.telematics.service;

import com.acme.insurance.telematics.model.Driver;
import com.acme.insurance.telematics.model.DriverState;
import com.acme.insurance.telematics.model.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriverManagerTest {

    private DriverManager driverManager;
    private FileBasedRouteService mockRouteService;
    private DestinationRouteService mockDestinationRouteService;

    @BeforeEach
    void setUp() {
        mockRouteService = mock(FileBasedRouteService.class);
        mockDestinationRouteService = mock(DestinationRouteService.class);
        
        // Create mock route data
        List<RoutePoint> mockRoute = List.of(
            new RoutePoint(33.7490, -84.3880, "Test Street Start", 35, false, "none"),
            new RoutePoint(33.7500, -84.3890, "Test Street End", 35, false, "none")
        );
        
        when(mockRouteService.getRandomRoute()).thenReturn(mockRoute);
        
        driverManager = new DriverManager(mockRouteService, mockDestinationRouteService);
        ReflectionTestUtils.setField(driverManager, "driverCount", 2);
        ReflectionTestUtils.setField(driverManager, "crashFrequency", 10);
        ReflectionTestUtils.setField(driverManager, "postCrashIdleMinutes", 1);
        ReflectionTestUtils.setField(driverManager, "randomStopProbability", 0.1);
        ReflectionTestUtils.setField(driverManager, "breakDurationMinutes", 1);
    }

    @Test
    void initializeDrivers_shouldCreateCorrectNumberOfDrivers() {
        driverManager.initializeDrivers("TEST-POLICY", 33.7490, -84.3880);
        
        assertThat(driverManager.getDriverCount()).isEqualTo(2);
        assertThat(driverManager.getAllDrivers()).hasSize(2);
        
        assertThat(driverManager.getAllDrivers().get(0).getDriverId()).isEqualTo("DRIVER-001");
        assertThat(driverManager.getAllDrivers().get(1).getDriverId()).isEqualTo("DRIVER-002");
        
        // Verify drivers are positioned at route start points
        assertThat(driverManager.getAllDrivers().get(0).getCurrentLatitude()).isEqualTo(33.7490);
        assertThat(driverManager.getAllDrivers().get(0).getCurrentLongitude()).isEqualTo(-84.3880);
    }

    @Test
    void selectDriverForMessage_shouldReturnDriver() {
        driverManager.initializeDrivers("TEST-POLICY", 33.7490, -84.3880);
        
        Driver selectedDriver = driverManager.selectDriverForMessage();
        
        assertThat(selectedDriver).isNotNull();
        assertThat(selectedDriver.getDriverId()).matches("DRIVER-\\d{3}");
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
        
        // Initial state should be PARKED
        assertThat(driver.getCurrentState()).isEqualTo(DriverState.PARKED);
        
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
        Driver driver = new Driver("TEST-001", "TEST-POLICY-001", 33.7490, -84.3880);
        
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
        Driver driver = new Driver("TEST-001", "TEST-POLICY-001", 33.7490, -84.3880);
        driver.setCurrentState(DriverState.DRIVING);
        driver.setCurrentSpeed(35.0);
        
        driver.recordCrashEvent();
        
        assertThat(driver.getCurrentState()).isEqualTo(DriverState.POST_CRASH_IDLE);
        assertThat(driver.getCurrentSpeed()).isEqualTo(0.0);
        assertThat(driver.getLastCrashTime()).isNotNull();
        assertThat(driver.isStationary()).isTrue();
    }
}