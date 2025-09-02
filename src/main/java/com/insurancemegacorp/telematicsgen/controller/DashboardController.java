package com.insurancemegacorp.telematicsgen.controller;

import com.insurancemegacorp.telematicsgen.service.DriverManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@CrossOrigin(origins = "*", maxAge = 3600)
public class DashboardController {

    private final DriverManager driverManager;

    public DashboardController(DriverManager driverManager) {
        this.driverManager = driverManager;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("driverCount", driverManager.getDriverCount());
        return "dashboard";
    }

    @GetMapping("/api/drivers")
    @ResponseBody
    public Object getAllDrivers() {
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
    }

    @GetMapping("/api/health")
    @ResponseBody
    public Object getHealth() {
        return new Object() {
            public final String status = "OK";
            public final int driverCount = driverManager.getDriverCount();
            public final String timestamp = java.time.Instant.now().toString();
        };
    }
}