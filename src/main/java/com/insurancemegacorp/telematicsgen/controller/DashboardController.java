package com.insurancemegacorp.telematicsgen.controller;

import com.insurancemegacorp.telematicsgen.service.DriverManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@CrossOrigin(originPatterns = "*", maxAge = 3600)
public class DashboardController {

    private final DriverManager driverManager;

    public DashboardController(DriverManager driverManager) {
        this.driverManager = driverManager;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        try {
            model.addAttribute("driverCount", driverManager.getDriverCount());
        } catch (Exception e) {
            // Handle case where drivers haven't been initialized yet
            model.addAttribute("driverCount", 0);
        }
        return "dashboard";
    }

    @GetMapping("/api/drivers")
    @ResponseBody
    public Object getAllDrivers() {
        try {
            return driverManager.getAllDrivers().stream()
                .map(driver -> new Object() {
                    public final int driverId = driver.getDriverId();
                    public final int policyId = driver.getPolicyId();
                    public final int vehicleId = driver.getVehicleId();
                    public final double latitude = driver.getCurrentLatitude();
                    public final double longitude = driver.getCurrentLongitude();
                    public final double bearing = driver.getCurrentBearing();
                    public final double speedMph = driver.getCurrentSpeed();
                    public final String currentStreet = driver.getCurrentStreet();
                    public final String state = driver.getCurrentState().toString();
                })
                .toList();
        } catch (Exception e) {
            // Return empty list if drivers haven't been initialized yet
            return List.of();
        }
    }

    @GetMapping("/api/health")
    @ResponseBody
    public Object getHealth() {
        try {
            return new Object() {
                public final String status = "OK";
                public final int driverCount = driverManager.getDriverCount();
                public final String timestamp = java.time.Instant.now().toString();
            };
        } catch (Exception e) {
            return new Object() {
                public final String status = "OK";
                public final int driverCount = 0;
                public final String timestamp = java.time.Instant.now().toString();
                public final String message = "Drivers not initialized yet";
            };
        }
    }

    @PostMapping("/api/drivers/start-all")
    @ResponseBody
    public Object startAllDriving() {
        try {
            int startedCount = driverManager.startAllDriving();
            return new Object() {
                public final String status = "OK";
                public final int driversStarted = startedCount;
                public final String message = startedCount + " drivers started driving";
                public final String timestamp = java.time.Instant.now().toString();
            };
        } catch (Exception e) {
            return new Object() {
                public final String status = "ERROR";
                public final String message = "Failed to start drivers: " + e.getMessage();
                public final String timestamp = java.time.Instant.now().toString();
            };
        }
    }
}