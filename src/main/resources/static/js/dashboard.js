class TelematicsDashboard {
    constructor() {
        this.map = null;
        this.drivers = new Map();
        this.stompClient = null;
        this.crashCount = 0;
        this.events = [];
        this.maxEvents = 20;
        this.randomAccidentsEnabled = false;
        this.darkModeEnabled = false;
        
        this.loadSettings();
        this.init();
    }

    init() {
        this.initMap();
        this.connectWebSocket();
        this.loadInitialData();
        this.updateUIFromSettings();

        // Wire controls
        const rateSlider = document.getElementById('rateSlider');
        const rateValue = document.getElementById('rateValue');
        if (rateSlider && rateValue) {
            rateSlider.addEventListener('input', () => {
                rateValue.textContent = rateSlider.value;
            });
            rateSlider.addEventListener('change', async () => {
                try {
                    await fetch(`/api/interval?ms=${rateSlider.value}`, { method: 'POST' });
                } catch (e) { console.error('Failed to set interval', e); }
            });
        }
        
        // Load current interval after controls are wired
        this.loadCurrentInterval();
    }

    async loadCurrentInterval() {
        try {
            const response = await fetch('/api/interval');
            if (response.ok) {
                const data = await response.json();
                const currentInterval = data.interval || 50; // Default to 50ms (center of 10-100ms range)
                
                // Update the slider and display
                const rateSlider = document.getElementById('rateSlider');
                const rateValue = document.getElementById('rateValue');
                
                if (rateSlider && rateValue) {
                    rateSlider.value = currentInterval;
                    rateValue.textContent = currentInterval;
                }
            }
        } catch (e) {
            console.error('Failed to load current interval:', e);
            // Set default value if API call fails
            const rateSlider = document.getElementById('rateSlider');
            const rateValue = document.getElementById('rateValue');
            if (rateSlider && rateValue) {
                rateSlider.value = 50;
                rateValue.textContent = 50;
            }
        }
    }

    loadSettings() {
        try {
            this.randomAccidentsEnabled = localStorage.getItem('randomAccidentsEnabled') === 'true';
            this.darkModeEnabled = localStorage.getItem('darkModeEnabled') === 'true';
        } catch (e) {
            console.warn('Failed to load settings from localStorage:', e);
        }
    }

    saveSettings() {
        try {
            localStorage.setItem('randomAccidentsEnabled', this.randomAccidentsEnabled);
            localStorage.setItem('darkModeEnabled', this.darkModeEnabled);
        } catch (e) {
            console.warn('Failed to save settings to localStorage:', e);
        }
    }

    updateUIFromSettings() {
        // Update random accidents UI
        this.updateRandomAccidentsUI();
        
        // Apply dark mode if enabled
        if (this.darkModeEnabled) {
            document.body.classList.add('dark-mode');
            this.updateDarkModeButton(true);
            // Apply dark map theme (will be called after map is initialized)
            setTimeout(() => this.toggleMapTheme(true), 100);
        }
    }

    updateRandomAccidentsUI() {
        const button = document.getElementById('randomAccidentsBtn');
        const status = document.getElementById('randomAccidentsStatus');

        if (button) {
            button.textContent = `Random Accidents: ${this.randomAccidentsEnabled ? 'ON' : 'OFF'}`;
            button.classList.toggle('active', this.randomAccidentsEnabled);
        }

        if (status) {
            status.textContent = this.randomAccidentsEnabled ? 'ON' : 'OFF';
            status.className = `stat-value ${this.randomAccidentsEnabled ? 'driving' : ''}`;
        }
    }

    updateDarkModeButton(enabled) {
        const button = document.getElementById('darkModeToggle');
        if (button) {
            button.classList.toggle('active', enabled);
            button.title = enabled ? 'Switch to light mode' : 'Switch to dark mode';

            // Toggle between moon and sun icons
            const moonIcon = button.querySelector('.moon-icon');
            const sunIcon = button.querySelector('.sun-icon');
            if (moonIcon && sunIcon) {
                moonIcon.style.display = enabled ? 'none' : 'block';
                sunIcon.style.display = enabled ? 'block' : 'none';
            }
        }
    }

    showStatusMessage(message, type = 'success', duration = 3000) {
        // Remove existing status message
        const existing = document.querySelector('.status-message');
        if (existing) {
            existing.remove();
        }

        // Create new status message
        const statusDiv = document.createElement('div');
        statusDiv.className = `status-message ${type}`;
        statusDiv.textContent = message;
        document.body.appendChild(statusDiv);

        // Show with animation
        setTimeout(() => statusDiv.classList.add('show'), 10);

        // Auto-hide after duration
        setTimeout(() => {
            statusDiv.classList.remove('show');
            setTimeout(() => statusDiv.remove(), 300);
        }, duration);
    }

    initMap() {
        // Center on Atlanta
        // Zoom out to level 9 to ensure the 80-mile boundary is visible
        this.map = L.map('map').setView([33.7490, -84.3880], 9);
        
        // Create light and dark tile layers
        this.lightTileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors'
        });
        
        this.darkTileLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            attribution: '© OpenStreetMap contributors, © CARTO'
        });
        
        // Add initial tile layer based on dark mode setting
        (this.darkModeEnabled ? this.darkTileLayer : this.lightTileLayer).addTo(this.map);

        // Add Atlanta area outline
        this.addAtlantaBounds();

        // Wire up map overlay controls
        const fitAllBtn = document.getElementById('fitAllBtn');
        if (fitAllBtn) {
            fitAllBtn.addEventListener('click', () => this.fitMapToAllDrivers());
        }
        const filterDriving = document.getElementById('filterDriving');
        const filterParked = document.getElementById('filterParked');
        const filterCrash = document.getElementById('filterCrash');
        [filterDriving, filterParked, filterCrash].forEach(cb => cb && cb.addEventListener('change', () => this.updateDriversList()));
        const followSelected = document.getElementById('followSelected');
        if (followSelected) {
            followSelected.addEventListener('change', () => this.updateFollowMode());
        }
    }

    toggleMapTheme(darkMode) {
        if (!this.map || !this.lightTileLayer || !this.darkTileLayer) return;
        
        if (darkMode) {
            this.map.removeLayer(this.lightTileLayer);
            this.map.addLayer(this.darkTileLayer);
        } else {
            this.map.removeLayer(this.darkTileLayer);
            this.map.addLayer(this.lightTileLayer);
        }
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
                        vehicle_id: driver.vehicleId,
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
        // Count a crash only on transition into crash state
        const isCrashEvent = (!wasCrashed && isCrashed);
        
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

        // Follow selected driver if enabled
        const followSelected = document.getElementById('followSelected');
        const driverSelect = document.getElementById('driverSelect');
        if (followSelected && followSelected.checked && driverSelect && driverSelect.value === driverUpdate.driver_id) {
            this.map.panTo([driverUpdate.latitude, driverUpdate.longitude]);
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
            html: `<div style="width: 100%; height: 100%; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: bold; color: white;">${driverUpdate.driver_id}</div>`
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
                <p><strong>Vehicle ID:</strong> ${driverUpdate.vehicle_id ?? 'N/A'}</p>
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

        // Update driver count badge
        const badge = document.getElementById('driverCountBadge');
        if (badge) {
            badge.textContent = `${stats.total} active`;
        }
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
        const filterDriving = document.getElementById('filterDriving');
        const filterParked = document.getElementById('filterParked');
        const filterCrash = document.getElementById('filterCrash');
        const filtered = driversArray.filter(d =>
            ((filterDriving?.checked ?? true) && d.state === 'DRIVING') ||
            ((filterParked?.checked ?? true) && (d.state === 'PARKED' || d.state === 'TRAFFIC_STOP' || d.state === 'BREAK_TIME')) ||
            ((filterCrash?.checked ?? true) && (d.is_crash_event || d.state === 'POST_CRASH_IDLE'))
        );
        
        driversList.innerHTML = filtered.map(driver => {
            const stateClass = driver.is_crash_event || driver.state === 'POST_CRASH_IDLE' ? 'crash' :
                              driver.state === 'DRIVING' ? 'driving' : 'parked';

            // Get initials from driver_id (e.g., "D1" or first 2 chars)
            const initials = String(driver.driver_id).substring(0, 2).toUpperCase();

            // Format state for display
            const stateDisplay = driver.state.replace(/_/g, ' ').toLowerCase()
                .replace(/\b\w/g, c => c.toUpperCase());

            return `
                <div class="driver-item ${stateClass}" onclick="dashboardInstance.focusOnDriver(${driver.driver_id})">
                    <div class="driver-avatar">${initials}</div>
                    <div class="driver-details">
                        <div class="driver-name">Driver ${driver.driver_id}</div>
                        <div class="driver-meta">
                            <span class="driver-speed">${driver.speed_mph.toFixed(0)} mph</span>
                            <span class="driver-street">${driver.current_street || 'Unknown'}</span>
                        </div>
                    </div>
                    <span class="driver-status-badge">${stateDisplay}</span>
                </div>
            `;
        }).join('');
        
        // Update the driver selection dropdown
        this.updateDriverDropdown();
    }

    fitMapToAllDrivers() {
        const markers = Array.from(this.drivers.values());
        if (!markers.length) return;
        const bounds = L.latLngBounds(markers.map(d => [d.latitude, d.longitude]));
        this.map.fitBounds(bounds.pad(0.2));
    }

    focusOnDriver(driverId) {
        const driver = this.drivers.get(driverId);
        if (driver) {
            this.map.setView([driver.latitude, driver.longitude], 14, {
                animate: true,
                duration: 0.5
            });
            // Open popup if marker exists
            if (driver.marker) {
                driver.marker.openPopup();
            }
        }
    }

    updateFollowMode() {
        // No-op; handled on each update
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
        driverSelect.innerHTML = '<option value="random">Random Driver</option>';
        
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
            // Distinguish between random and manual crashes
            const crashType = driverUpdate.manual_crash ? 'Manual' : 'Random';
            eventText = `${crashType} crash: Driver ${driverUpdate.driver_id} on ${driverUpdate.current_street}`;
            eventClass = 'crash';
        } else if (driverUpdate.state === 'DRIVING' && this.events.length === 0) {
            eventText = `Driver ${driverUpdate.driver_id} started on ${driverUpdate.current_street}`;
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
                <div class="event-text">${event.text}</div>
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
        // Subscribe BEFORE sending to avoid missing the response
        if (dashboardInstance.accidentSubscription) {
            try { dashboardInstance.accidentSubscription.unsubscribe(); } catch (_) {}
        }
        const timeoutHandle = setTimeout(() => {
            // Fallback if no response arrives
            button.disabled = false;
            button.innerHTML = '🚨 Trigger Accident';
            statusDiv.innerHTML = '⚠️ No response received';
            statusDiv.className = 'accident-status error';
            if (dashboardInstance.accidentSubscription) {
                try { dashboardInstance.accidentSubscription.unsubscribe(); } catch (_) {}
                dashboardInstance.accidentSubscription = null;
            }
        }, 7000);

        dashboardInstance.accidentSubscription = dashboardInstance.stompClient.subscribe('/topic/drivers/accident', (message) => {
            const response = JSON.parse(message.body);
            console.log('Accident response:', response);
            clearTimeout(timeoutHandle);
            if (response.success) {
                statusDiv.innerHTML = `✅ ${response.message}`;
                statusDiv.className = 'accident-status success';
                // Show the accident notification popup
                showAccidentModal(response);
            } else {
                statusDiv.innerHTML = `❌ ${response.message}`;
                statusDiv.className = 'accident-status error';
            }
            // Re-enable button and unsubscribe after short delay
            setTimeout(() => {
                button.disabled = false;
                button.innerHTML = '🚨 Trigger Accident';
                statusDiv.innerHTML = '';
                statusDiv.className = 'accident-status';
                if (dashboardInstance.accidentSubscription) {
                    try { dashboardInstance.accidentSubscription.unsubscribe(); } catch (_) {}
                    dashboardInstance.accidentSubscription = null;
                }
            }, 2000);
        });

        // Send appropriate accident trigger request via WebSocket
        if (isRandom) {
            dashboardInstance.stompClient.send('/app/drivers/trigger-accident', {}, JSON.stringify({}));
        } else {
            dashboardInstance.stompClient.send('/app/drivers/trigger-accident-specific', {}, selectedDriver);
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

// Pause / Resume
async function togglePause() {
    const btn = document.getElementById('pauseResumeBtn');
    if (!btn) return;
    btn.disabled = true;
    try {
        // naive toggle: read current label to decide
        const isPause = btn.textContent.includes('Pause');
        const endpoint = isPause ? '/api/pause' : '/api/resume';
        const resp = await fetch(endpoint, { method: 'POST' });
        const data = await resp.json();
        if (data && 'paused' in data) {
            btn.textContent = data.paused ? '▶️ Resume Generation' : '⏸️ Pause Generation';
        } else {
            // fallback toggle
            btn.textContent = isPause ? '▶️ Resume Generation' : '⏸️ Pause Generation';
        }
    } catch (e) {
        console.error('Failed to toggle pause', e);
    } finally {
        btn.disabled = false;
    }
}

// Start All Driving
async function startAllDriving() {
    const btn = document.getElementById('startAllDrivingBtn');
    const statusDiv = document.getElementById('accidentStatus');
    if (!btn) return;
    btn.disabled = true;
    try {
        const resp = await fetch('/api/drivers/start-all', { method: 'POST' });
        const data = await resp.json();
        if (data.status === 'OK') {
            if (statusDiv) {
                statusDiv.innerHTML = `🚦 ${data.message}`;
                statusDiv.className = 'accident-status success';
                setTimeout(() => { statusDiv.innerHTML = ''; }, 3000);
            }
        } else {
            if (statusDiv) {
                statusDiv.innerHTML = `❌ ${data.message}`;
                statusDiv.className = 'accident-status error';
            }
        }
    } catch (e) {
        console.error('Failed to start all driving', e);
        if (statusDiv) {
            statusDiv.innerHTML = '❌ Failed to start drivers';
            statusDiv.className = 'accident-status error';
        }
    } finally {
        btn.disabled = false;
    }
}

// Random Accidents Toggle
function toggleRandomAccidents() {
    if (!dashboardInstance) {
        console.error('Dashboard not initialized');
        return;
    }
    
    const newState = !dashboardInstance.randomAccidentsEnabled;
    
    if (!dashboardInstance.stompClient) {
        dashboardInstance.showStatusMessage('❌ Cannot toggle - connection lost', 'error');
        // Try to reconnect
        dashboardInstance.connectWebSocket();
        return;
    }
    
    // Show loading state
    dashboardInstance.showStatusMessage('⏳ Updating random accidents...', 'loading', 1000);
    
    try {
        // Subscribe to response before sending
        const subscription = dashboardInstance.stompClient.subscribe('/topic/sim/random-accidents', (message) => {
            const response = JSON.parse(message.body);
            
            if (response.enabled !== undefined) {
                dashboardInstance.randomAccidentsEnabled = response.enabled;
                dashboardInstance.updateRandomAccidentsUI();
                dashboardInstance.saveSettings();
                
                dashboardInstance.showStatusMessage(
                    `✅ Random accidents ${response.enabled ? 'enabled' : 'disabled'}`, 
                    'success'
                );
            }
            
            // Unsubscribe after handling
            subscription.unsubscribe();
        });
        
        // Send toggle request
        dashboardInstance.stompClient.send('/app/sim/toggle-random-accidents', {}, String(newState));
        
        // Set timeout for no response
        setTimeout(() => {
            try {
                subscription.unsubscribe();
            } catch (e) { /* ignore */ }
        }, 5000);
        
    } catch (error) {
        console.error('Error toggling random accidents:', error);
        dashboardInstance.showStatusMessage('❌ Failed to toggle random accidents', 'error');
    }
}

// Dark Mode Toggle
function toggleDarkMode() {
    if (!dashboardInstance) {
        console.error('Dashboard not initialized');
        return;
    }
    
    dashboardInstance.darkModeEnabled = !dashboardInstance.darkModeEnabled;
    
    // Apply/remove dark mode class
    document.body.classList.toggle('dark-mode', dashboardInstance.darkModeEnabled);
    
    // Toggle map theme
    dashboardInstance.toggleMapTheme(dashboardInstance.darkModeEnabled);
    
    // Update button
    dashboardInstance.updateDarkModeButton(dashboardInstance.darkModeEnabled);
    
    // Save setting
    dashboardInstance.saveSettings();
    
    // Show feedback
    dashboardInstance.showStatusMessage(
        `✅ Dark mode ${dashboardInstance.darkModeEnabled ? 'enabled' : 'disabled'}`,
        'success',
        2000
    );
}

// Accident type display names and image mappings
const ACCIDENT_TYPE_INFO = {
    'REAR_ENDED': { name: 'Rear-Ended', description: 'Vehicle was struck from behind' },
    'REAR_END_COLLISION': { name: 'Rear-End Collision', description: 'Vehicle struck another from behind' },
    'T_BONE': { name: 'T-Bone Collision', description: 'Side impact from perpendicular vehicle' },
    'SIDE_SWIPE': { name: 'Side-Swipe', description: 'Glancing side impact while traveling' },
    'HEAD_ON': { name: 'Head-On Collision', description: 'Frontal collision with oncoming vehicle' },
    'ROLLOVER': { name: 'Rollover', description: 'Vehicle rolled over during accident' },
    'SINGLE_VEHICLE': { name: 'Single Vehicle', description: 'Collision with fixed object' },
    'MULTI_VEHICLE_PILEUP': { name: 'Multi-Vehicle Pileup', description: 'Chain-reaction collision' },
    'HIT_AND_RUN': { name: 'Hit and Run', description: 'Struck by vehicle that fled scene' }
};

// Show accident notification modal
function showAccidentModal(accidentData) {
    const modal = document.getElementById('accidentModal');
    if (!modal) return;

    const accidentType = accidentData.accident_type || 'UNKNOWN';
    const typeInfo = ACCIDENT_TYPE_INFO[accidentType] || { name: accidentType, description: '' };

    // Set accident image
    const imgElement = document.getElementById('accidentImage');
    imgElement.src = `/images/accidents/${accidentType.toLowerCase()}.png`;
    imgElement.alt = typeInfo.name;
    imgElement.onerror = function() {
        // Fallback if image not found
        this.src = '/images/accidents/default.png';
        this.onerror = null;
    };

    // Set accident type name
    document.getElementById('accidentTypeName').textContent = typeInfo.name;

    // Set driver info
    document.getElementById('accidentDriver').textContent =
        accidentData.driver_name || `Driver ${accidentData.driver_id}`;

    // Set vehicle
    document.getElementById('accidentVehicle').textContent = accidentData.vehicle || 'Unknown';

    // Set location
    document.getElementById('accidentLocation').textContent = accidentData.street || 'Unknown location';

    // Set speed at impact with speeding indicator
    const speedAtImpact = accidentData.speed_at_impact || 0;
    const speedLimit = accidentData.speed_limit || 0;
    const speedCell = document.getElementById('accidentSpeed');
    const wasSpeeding = speedAtImpact > speedLimit;
    speedCell.textContent = `${speedAtImpact.toFixed(1)} mph${wasSpeeding ? ' ⚠️ SPEEDING' : ''}`;
    speedCell.className = wasSpeeding ? 'speeding' : '';

    // Set speed limit
    document.getElementById('accidentSpeedLimit').textContent = `${speedLimit} mph`;

    // Set G-force
    const gForce = accidentData.g_force || 0;
    document.getElementById('accidentGForce').textContent = `${gForce.toFixed(2)}g`;

    // Set time
    const timestamp = accidentData.timestamp ? new Date(accidentData.timestamp) : new Date();
    document.getElementById('accidentTime').textContent = timestamp.toLocaleTimeString();

    // Show modal
    modal.style.display = 'flex';

    // Auto-close after 10 seconds
    setTimeout(() => {
        closeAccidentModal();
    }, 10000);
}

// Close accident modal
function closeAccidentModal() {
    const modal = document.getElementById('accidentModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

// Close modal when clicking outside
document.addEventListener('click', function(event) {
    const modal = document.getElementById('accidentModal');
    if (event.target === modal) {
        closeAccidentModal();
    }
});

// Close modal with Escape key
document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        closeAccidentModal();
    }
});