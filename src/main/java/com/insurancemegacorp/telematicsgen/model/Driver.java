package com.insurancemegacorp.telematicsgen.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Driver {
    private final int driverId;
    private final int policyId;
    private final int vehicleId;
    private final String vin;
    private final double baseLatitude;
    private final double baseLongitude;
    private final boolean aggressive;
    private final AtomicLong messageCount;
    
    private volatile DriverState currentState;
    private volatile Instant stateChangeTime;
    private volatile Instant lastCrashTime;
    private volatile double currentLatitude;
    private volatile double currentLongitude;
    private volatile double currentSpeed;
    private int speedLimit;
    
    // Route-based movement
    private volatile List<RoutePoint> currentRoute;
    private volatile int routeIndex;
    private volatile double currentBearing;
    private volatile String currentStreet;
    
    // Destination-based routing
    private volatile Destination currentDestination;
    private volatile double tripProgressPercent;
    private volatile Instant tripStartTime;

    // Crash event snapshot data (frozen during POST_CRASH_IDLE)
    private volatile Double crashSpeedAtImpact;
    private volatile String crashAccidentType;

    public Driver(int driverId, int policyId, int vehicleId, String vin, double baseLatitude, double baseLongitude) {
        this(driverId, policyId, vehicleId, vin, baseLatitude, baseLongitude, false);
    }

    public Driver(int driverId, int policyId, int vehicleId, String vin, double baseLatitude, double baseLongitude, boolean aggressive) {
        this.driverId = driverId;
        this.policyId = policyId;
        this.vehicleId = vehicleId;
        this.vin = vin;
        this.baseLatitude = baseLatitude;
        this.baseLongitude = baseLongitude;
        this.aggressive = aggressive;
        this.currentLatitude = baseLatitude;
        this.currentLongitude = baseLongitude;
        this.messageCount = new AtomicLong(0);
        this.currentState = DriverState.PARKED;
        this.stateChangeTime = Instant.now();
        this.currentSpeed = 0.0;
        this.speedLimit = 0;
        this.routeIndex = 0;
        this.currentBearing = 0.0;
        this.currentStreet = "Unknown";
    }

    public int getDriverId() {
        return driverId;
    }

    public int getPolicyId() {
        return policyId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public String getVin() {
        return vin;
    }

    public double getBaseLatitude() {
        return baseLatitude;
    }

    public double getBaseLongitude() {
        return baseLongitude;
    }

    public boolean isAggressive() {
        return aggressive;
    }

    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public DriverState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(DriverState newState) {
        if (this.currentState != newState) {
            this.currentState = newState;
            this.stateChangeTime = Instant.now();
        }
    }

    public Instant getStateChangeTime() {
        return stateChangeTime;
    }

    public Instant getLastCrashTime() {
        return lastCrashTime;
    }

    public void recordCrashEvent() {
        // Capture speed at impact before setting to zero
        this.crashSpeedAtImpact = this.currentSpeed;
        this.lastCrashTime = Instant.now();
        setCurrentState(DriverState.POST_CRASH_IDLE);
        setCurrentSpeed(0.0);
    }

    public void recordCrashEvent(String accidentType) {
        // Capture speed at impact and accident type before setting to zero
        this.crashSpeedAtImpact = this.currentSpeed;
        this.crashAccidentType = accidentType;
        this.lastCrashTime = Instant.now();
        setCurrentState(DriverState.POST_CRASH_IDLE);
        setCurrentSpeed(0.0);
    }

    public Double getCrashSpeedAtImpact() {
        return crashSpeedAtImpact;
    }

    public String getCrashAccidentType() {
        return crashAccidentType;
    }

    public void clearCrashData() {
        this.crashSpeedAtImpact = null;
        this.crashAccidentType = null;
    }

    public long getMessageCount() {
        return messageCount.get();
    }

    public long incrementMessageCount() {
        return messageCount.incrementAndGet();
    }

    public boolean isStationary() {
        return currentState == DriverState.PARKED || 
               currentState == DriverState.POST_CRASH_IDLE || 
               currentState == DriverState.TRAFFIC_STOP ||
               currentState == DriverState.BREAK_TIME;
    }

    public long getTimeInCurrentStateSeconds() {
        return java.time.Duration.between(stateChangeTime, Instant.now()).getSeconds();
    }

    public long getTimeSinceCrashSeconds() {
        if (lastCrashTime == null) {
            return Long.MAX_VALUE;
        }
        return java.time.Duration.between(lastCrashTime, Instant.now()).getSeconds();
    }

    // Route-based movement getters and setters
    public List<RoutePoint> getCurrentRoute() {
        return currentRoute;
    }

    public void setCurrentRoute(List<RoutePoint> currentRoute) {
        this.currentRoute = currentRoute;
        this.routeIndex = 0; // Reset to beginning of route
    }

    public int getRouteIndex() {
        return routeIndex;
    }

    public void setRouteIndex(int routeIndex) {
        this.routeIndex = routeIndex;
    }

    public double getCurrentBearing() {
        return currentBearing;
    }

    public void setCurrentBearing(double currentBearing) {
        this.currentBearing = currentBearing;
    }

    public String getCurrentStreet() {
        return currentStreet;
    }

    public void setCurrentStreet(String currentStreet) {
        this.currentStreet = currentStreet;
    }
    
    // Destination-based routing getters and setters
    public Destination getCurrentDestination() {
        return currentDestination;
    }
    
    public void setCurrentDestination(Destination currentDestination) {
        this.currentDestination = currentDestination;
        this.tripProgressPercent = 0.0;
        this.tripStartTime = Instant.now();
    }
    
    public double getTripProgressPercent() {
        return tripProgressPercent;
    }
    
    public void setTripProgressPercent(double tripProgressPercent) {
        this.tripProgressPercent = tripProgressPercent;
    }
    
    public Instant getTripStartTime() {
        return tripStartTime;
    }
    
    public long getTripDurationSeconds() {
        if (tripStartTime == null) {
            return 0;
        }
        return java.time.Duration.between(tripStartTime, Instant.now()).getSeconds();
    }
    
    public boolean hasReachedDestination() {
        return tripProgressPercent >= 100.0;
    }

    public int getSpeedLimit() {
        return speedLimit;
    }

    public void setSpeedLimit(int speedLimit) {
        this.speedLimit = speedLimit;
    }
}