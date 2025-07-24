package com.acme.insurance.telematics.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShutdownController {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdown() {
        logger.info("🛑 Shutdown request received from web dashboard");
        
        // Return response immediately
        ResponseEntity<Map<String, String>> response = ResponseEntity.ok(
            Map.of(
                "status", "success",
                "message", "Application shutdown initiated"
            )
        );
        
        // Schedule shutdown in a separate thread to allow response to be sent
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Give time for response to be sent
                logger.info("🛑 Shutting down telematics application...");
                SpringApplication.exit(applicationContext, () -> 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Shutdown interrupted", e);
            }
        }, "shutdown-thread").start();
        
        return response;
    }
}