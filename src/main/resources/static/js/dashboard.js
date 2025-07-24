class TelematicsDashboard {
    constructor() {
        this.map = null;
        this.drivers = new Map();
        this.stompClient = null;
        this.crashCount = 0;
        this.events = [];
        this.maxEvents = 20;
        
        this.init();
    }

    init() {
        this.initMap();
        this.connectWebSocket();
        this.loadInitialData();
    }

    initMap() {
        // Center on Atlanta
        // Zoom out to level 9 to ensure the 80-mile boundary is visible
        this.map = L.map('map').setView([33.7490, -84.3880], 9);
        
        // Add OpenStreetMap tiles
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors'
        }).addTo(this.map);

        // Add Atlanta area outline
        this.addAtlantaBounds();
    }

    addAtlantaBounds() {
        // Atlanta center coordinates
        const atlantaCenter = [33.7490, -84.3880];
        
        // 50-mile radius circle (50 miles ≈ 80.5 km)
        const radiusInMeters = 50 * 1609.34; // Convert miles to meters
        
        L.circle(atlantaCenter, {
            color: '#667eea',
            weight: 2,
            fillOpacity: 0.1,
            fillColor: '#667eea',
            radius: radiusInMeters
        }).addTo(this.map);
        
    }

    connectWebSocket() {
        console.log('Attempting to connect to WebSocket...');
        
        // Disconnect existing connection if any
        if (this.stompClient && this.stompClient.connected) {
            this.stompClient.disconnect();
        }
        
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        
        // Disable debug logging to reduce console noise
        this.stompClient.debug = null;
        
        // Set heartbeat to keep connection alive
        this.stompClient.heartbeat.outgoing = 20000;
        this.stompClient.heartbeat.incoming = 20000;
        
        this.stompClient.connect({}, (frame) => {
            console.log('✅ WebSocket Connected');
            this.updateConnectionStatus(true);
            this.reconnectAttempts = 0; // Reset reconnect attempts on successful connection
            
            // Subscribe to driver updates
            this.stompClient.subscribe('/topic/drivers', (message) => {
                try {
                    const driverUpdate = JSON.parse(message.body);
                    this.updateDriver(driverUpdate);
                } catch (e) {
                    console.error('Error parsing driver update:', e);
                }
            });
            
            // Subscribe to bulk driver updates
            this.stompClient.subscribe('/topic/drivers/all', (message) => {
                try {
                    const allDrivers = JSON.parse(message.body);
                    allDrivers.forEach(driver => this.updateDriver(driver));
                } catch (e) {
                    console.error('Error parsing all drivers:', e);
                }
            });
            
            // Request initial driver data
            console.log('🔄 Requesting initial driver data...');
            this.stompClient.send('/app/drivers/request', {}, '{}');
            
        }, (error) => {
            console.error('❌ WebSocket connection error:', error);
            this.updateConnectionStatus(false);
            
            // Progressive backoff: wait longer each time
            const retryDelay = Math.min(30000, 5000 * Math.pow(2, this.reconnectAttempts || 0));
            this.reconnectAttempts = (this.reconnectAttempts || 0) + 1;
            
            console.log(`🔄 Retrying connection in ${retryDelay/1000}s (attempt ${this.reconnectAttempts})`);
            setTimeout(() => this.connectWebSocket(), retryDelay);
        });
    }

    updateConnectionStatus(connected) {
        const status = document.getElementById('connectionStatus');
        if (connected) {
            status.textContent = '🟢 Connected';
            status.className = 'connection-status connected';
        } else {
            status.textContent = '🔴 Disconnected';
            status.className = 'connection-status disconnected';
        }
    }

    loadInitialData() {
        console.log('🔄 Loading initial driver data via REST API...');
        fetch('/api/drivers')
            .then(response => {
                console.log('📡 REST API response status:', response.status);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                return response.json();
            })
            .then(drivers => {
                console.log('📊 Received drivers via REST:', drivers);
                drivers.forEach(driver => {
                    const driverUpdate = {
                        driver_id: driver.driverId,
                        policy_id: driver.policyId,
                        latitude: driver.latitude,
                        longitude: driver.longitude,
                        bearing: driver.bearing,
                        speed_mph: driver.speedMph,
                        current_street: driver.currentStreet,
                        state: driver.state,
                        route_description: 'Loading...',
                        is_crash_event: false,
                        g_force: 0,
                        timestamp: new Date().toISOString()
                    };
                    console.log('📍 Processing driver:', driverUpdate);
                    this.updateDriver(driverUpdate);
                });
            })
            .catch(error => {
                console.error('❌ Error loading initial data:', error);
            });
    }

    updateDriver(driverUpdate) {
        const existingDriver = this.drivers.get(driverUpdate.driver_id);
        
        // Simple crash detection for counter increment
        const wasCrashed = existingDriver && existingDriver.state === 'POST_CRASH_IDLE';
        const isCrashed = driverUpdate.state === 'POST_CRASH_IDLE';
        const isCrashEvent = driverUpdate.is_crash_event || (!wasCrashed && isCrashed);
        
        // Increment crash counter if this is a new crash
        if (isCrashEvent) {
            this.crashCount++;
        }
        
        // Update or create driver marker - treat all drivers the same
        if (existingDriver) {
            this.updateDriverMarker(existingDriver, driverUpdate);
        } else {
            this.createDriverMarker(driverUpdate);
        }
        
        // Store updated driver data
        this.drivers.set(driverUpdate.driver_id, driverUpdate);
        
        // Update UI panels
        this.updateStatsPanel();
        this.updateDriversList();
        this.addEvent(driverUpdate);
    }

    createDriverMarker(driverUpdate) {
        const marker = this.createMarkerForState(driverUpdate);
        marker.bindPopup(this.createPopupContent(driverUpdate));
        marker.addTo(this.map);
        
        // Store marker reference
        driverUpdate.marker = marker;
    }

    updateDriverMarker(existingDriver, driverUpdate) {
        const marker = existingDriver.marker;
        if (marker) {
            // Always use the same update logic for all state changes
            marker.setLatLng([driverUpdate.latitude, driverUpdate.longitude]);
            
            // Update icon if state changed
            if (existingDriver.state !== driverUpdate.state) {
                const newIcon = this.createIconForState(driverUpdate);
                marker.setIcon(newIcon);
                
            }
            
            // Update popup content
            marker.setPopupContent(this.createPopupContent(driverUpdate));
            
            // Store updated marker reference
            driverUpdate.marker = marker;
        }
    }

    createIconForState(driverUpdate) {
        let className = 'driver-marker-driving';
        // Use consistent size for all markers to prevent positioning issues
        const size = [20, 20];
        
        if (driverUpdate.is_crash_event || driverUpdate.state === 'POST_CRASH_IDLE') {
            className = 'driver-marker-crash';
        } else if (driverUpdate.state === 'PARKED' || driverUpdate.state === 'TRAFFIC_STOP' || driverUpdate.state === 'BREAK_TIME') {
            className = 'driver-marker-parked';
        }
        
        // Consistent anchor point centered for all markers
        const anchor = [10, 10]; // Centered anchor for 20x20 icons
        
        return L.divIcon({
            className: className,
            iconSize: size,
            iconAnchor: anchor,
            html: `<div style="width: 100%; height: 100%; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: bold; color: white;">${driverUpdate.driver_id.split('-')[1]}</div>`
        });
    }

    createMarkerForState(driverUpdate) {
        return L.marker([driverUpdate.latitude, driverUpdate.longitude], {
            icon: this.createIconForState(driverUpdate)
        });
    }

    createPopupContent(driverUpdate) {
        const gForceColor = driverUpdate.g_force > 2.0 ? '#f44336' : '#4caf50';
        
        return `
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                <h4>${driverUpdate.driver_id}</h4>
                <p><strong>Speed:</strong> ${driverUpdate.speed_mph.toFixed(1)} mph</p>
                <p><strong>State:</strong> ${driverUpdate.state}</p>
                <p><strong>Street:</strong> ${driverUpdate.current_street}</p>
                <p><strong>Route:</strong> ${driverUpdate.route_description}</p>
                <p><strong>G-Force:</strong> <span style="color: ${gForceColor}">${driverUpdate.g_force.toFixed(2)}g</span></p>
                <p><strong>📍 Location:</strong> ${driverUpdate.latitude.toFixed(6)}, ${driverUpdate.longitude.toFixed(6)}</p>
                <p style="font-size: 0.8em; color: #666;">
                    Updated: ${new Date(driverUpdate.timestamp).toLocaleTimeString()}
                </p>
            </div>
        `;
    }

    handleCrashEvent(driverUpdate) {
        this.crashCount++;
        
        // Flash the marker
        if (driverUpdate.marker) {
            const marker = driverUpdate.marker;
            let flashCount = 0;
            const flashInterval = setInterval(() => {
                marker.getElement().style.transform = flashCount % 2 === 0 ? 'scale(1.5)' : 'scale(1)';
                flashCount++;
                if (flashCount > 6) {
                    clearInterval(flashInterval);
                    marker.getElement().style.transform = 'scale(1)';
                }
            }, 300);
        }
        
        // Pan map to crash location
        this.map.setView([driverUpdate.latitude, driverUpdate.longitude], 15, {
            animate: true,
            duration: 1
        });
    }

    updateStatsPanel() {
        const stats = this.calculateStats();
        
        document.getElementById('activeDrivers').textContent = stats.total;
        document.getElementById('drivingCount').textContent = stats.driving;
        document.getElementById('parkedCount').textContent = stats.parked;
        document.getElementById('crashCount').textContent = this.crashCount;
    }

    calculateStats() {
        let total = 0, driving = 0, parked = 0;
        
        this.drivers.forEach(driver => {
            total++;
            if (driver.state === 'DRIVING') {
                driving++;
            } else {
                parked++;
            }
        });
        
        return { total, driving, parked };
    }

    updateDriversList() {
        const driversList = document.getElementById('driversList');
        const driversArray = Array.from(this.drivers.values());
        
        driversList.innerHTML = driversArray.map(driver => {
            const stateClass = driver.is_crash_event || driver.state === 'POST_CRASH_IDLE' ? 'crash' : 
                              driver.state === 'DRIVING' ? 'driving' : 'parked';
            
            return `
                <div class="driver-item ${stateClass}">
                    <div class="driver-id">${driver.driver_id}</div>
                    <div class="driver-info">
                        <div class="driver-speed">${driver.speed_mph.toFixed(1)} mph • ${driver.state}</div>
                        <div class="driver-street">${driver.current_street}</div>
                    </div>
                </div>
            `;
        }).join('');
        
        // Update the driver selection dropdown
        this.updateDriverDropdown();
    }
    
    updateDriverDropdown() {
        const driverSelect = document.getElementById('driverSelect');
        if (!driverSelect) return;
        
        // Preserve current selection
        const currentSelection = driverSelect.value;
        
        const driversArray = Array.from(this.drivers.values());
        const activeDrivers = driversArray.filter(driver => 
            !driver.state.includes('CRASH') && driver.state !== 'POST_CRASH_IDLE'
        );
        
        // Clear existing options except the first one (Random Driver)
        driverSelect.innerHTML = '<option value="random">🎲 Random Driver</option>';
        
        // Add active drivers to dropdown
        activeDrivers.forEach(driver => {
            const option = document.createElement('option');
            option.value = driver.driver_id;
            option.textContent = `${driver.driver_id} (${driver.state})`;
            driverSelect.appendChild(option);
        });
        
        // Restore selection if the driver is still available
        if (currentSelection && (currentSelection === 'random' || activeDrivers.find(d => d.driver_id === currentSelection))) {
            driverSelect.value = currentSelection;
        }
    }

    addEvent(driverUpdate) {
        let eventText = '';
        let eventClass = '';
        
        if (driverUpdate.is_crash_event) {
            eventText = `💥 ${driverUpdate.driver_id} crashed on ${driverUpdate.current_street}`;
            eventClass = 'crash';
        } else if (driverUpdate.state === 'DRIVING' && this.events.length === 0) {
            eventText = `🚗 ${driverUpdate.driver_id} started driving on ${driverUpdate.current_street}`;
        } else {
            return; // Don't add routine updates as events
        }
        
        const event = {
            text: eventText,
            time: new Date(driverUpdate.timestamp),
            class: eventClass
        };
        
        this.events.unshift(event);
        if (this.events.length > this.maxEvents) {
            this.events = this.events.slice(0, this.maxEvents);
        }
        
        this.updateEventsList();
    }

    updateEventsList() {
        const eventsList = document.getElementById('eventsList');
        
        eventsList.innerHTML = this.events.map(event => `
            <div class="event-item ${event.class}">
                <div>${event.text}</div>
                <div class="event-time">${event.time.toLocaleTimeString()}</div>
            </div>
        `).join('');
    }
}

// Global reference to dashboard instance for button access
let dashboardInstance = null;

// Initialize dashboard when page loads
document.addEventListener('DOMContentLoaded', () => {
    dashboardInstance = new TelematicsDashboard();
});

// Function to trigger accident based on dropdown selection
function triggerSelectedAccident() {
    const button = document.getElementById('triggerAccidentBtn');
    const statusDiv = document.getElementById('accidentStatus');
    const driverSelect = document.getElementById('driverSelect');
    
    if (!dashboardInstance || !dashboardInstance.stompClient) {
        statusDiv.innerHTML = 'WebSocket not connected!';
        statusDiv.className = 'accident-status error';
        return;
    }
    
    const selectedDriver = driverSelect.value;
    const isRandom = selectedDriver === 'random';
    
    // Disable button and show loading state
    button.disabled = true;
    button.innerHTML = '⏳ Triggering...';
    statusDiv.innerHTML = isRandom ? 'Requesting random accident...' : `Triggering accident for ${selectedDriver}...`;
    statusDiv.className = 'accident-status loading';
    
    try {
        // Send appropriate accident trigger request via WebSocket
        if (isRandom) {
            dashboardInstance.stompClient.send('/app/drivers/trigger-accident', {}, JSON.stringify({}));
        } else {
            dashboardInstance.stompClient.send('/app/drivers/trigger-accident-specific', {}, selectedDriver);
        }
        
        // Subscribe to accident response if not already subscribed
        if (!dashboardInstance.accidentSubscription) {
            dashboardInstance.accidentSubscription = dashboardInstance.stompClient.subscribe('/topic/drivers/accident', (message) => {
                const response = JSON.parse(message.body);
                console.log('Accident response:', response);
                
                if (response.success) {
                    statusDiv.innerHTML = `✅ ${response.message}`;
                    statusDiv.className = 'accident-status success';
                } else {
                    statusDiv.innerHTML = `❌ ${response.message}`;
                    statusDiv.className = 'accident-status error';
                }
                
                // Re-enable button after 3 seconds
                setTimeout(() => {
                    button.disabled = false;
                    button.innerHTML = '🚨 Trigger Accident';
                    statusDiv.innerHTML = '';
                    statusDiv.className = 'accident-status';
                }, 3000);
            });
        }
        
    } catch (error) {
        console.error('Error triggering accident:', error);
        statusDiv.innerHTML = 'Error: Failed to send request';
        statusDiv.className = 'accident-status error';
        
        // Re-enable button
        button.disabled = false;
        button.innerHTML = '🚨 Trigger Accident';
    }
}

// Function to stop the application
function stopApplication() {
    const button = document.getElementById('stopAppBtn');
    const statusDiv = document.getElementById('stopStatus');
    
    // Disable button and show loading state
    button.disabled = true;
    button.innerHTML = '⏳ Stopping...';
    statusDiv.innerHTML = 'Sending shutdown request...';
    statusDiv.className = 'stop-status';
    
    try {
        // Call the custom shutdown endpoint
        fetch('/api/shutdown', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                statusDiv.innerHTML = `✅ ${data.message}`;
                statusDiv.className = 'stop-status success';
                
                // Disconnect WebSocket
                if (dashboardInstance && dashboardInstance.stompClient) {
                    dashboardInstance.stompClient.disconnect();
                }
                
                // Update connection status
                const connectionStatus = document.getElementById('connectionStatus');
                connectionStatus.innerHTML = '🔴 Disconnected';
                connectionStatus.className = 'connection-status disconnected';
                
                // Show final message after delay
                setTimeout(() => {
                    statusDiv.innerHTML = '🛑 Application stopped. Refresh page to restart.';
                    statusDiv.className = 'stop-status success';
                }, 2000);
                
            } else {
                throw new Error(data.message || 'Shutdown failed');
            }
        })
        .catch(error => {
            console.error('Error stopping application:', error);
            statusDiv.innerHTML = `❌ Failed to stop application: ${error.message}`;
            statusDiv.className = 'stop-status error';
            
            // Re-enable button
            button.disabled = false;
            button.innerHTML = '🛑 Stop Application';
        });
        
    } catch (error) {
        console.error('Error stopping application:', error);
        statusDiv.innerHTML = 'Error: Failed to send shutdown request';
        statusDiv.className = 'stop-status error';
        
        // Re-enable button
        button.disabled = false;
        button.innerHTML = '🛑 Stop Application';
    }
}