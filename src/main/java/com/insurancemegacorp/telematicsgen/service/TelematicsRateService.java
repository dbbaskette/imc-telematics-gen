package com.insurancemegacorp.telematicsgen.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class TelematicsRateService {

    private final AtomicLong messagesSentInSecond = new AtomicLong(0);
    private final AtomicLong currentRate = new AtomicLong(0);

    public TelematicsRateService(MeterRegistry meterRegistry) {
        Gauge.builder("telematics.messages.rate", currentRate, AtomicLong::get)
             .description("The current rate of telematics messages sent per second")
             .register(meterRegistry);
    }

    public void incrementMessageCount() {
        messagesSentInSecond.incrementAndGet();
    }

    @Scheduled(fixedRate = 1000)
    public void calculateAndResetRate() {
        long rate = messagesSentInSecond.getAndSet(0);
        currentRate.set(rate);
    }
}
