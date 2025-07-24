package com.acme.insurance.telematics.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Standalone utility to generate route files using OpenRouteService API
 * Usage: Run this as a main method to generate route JSON files
 */
public class RouteGenerator {
    
    private static final String ORS_API_BASE = "https://api.openrouteservice.org/v2/directions/driving-car";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("🚗 Route Generator for Telematics Simulation");
        System.out.println("============================================");
        System.out.println("This utility generates route files using OpenRouteService API");
        System.out.println("Routes will be saved to: src/main/resources/routes/");
        System.out.println();
        
        // Create routes directory if it doesn't exist
        File routesDir = new File("src/main/resources/routes");
        if (!routesDir.exists()) {
            routesDir.mkdirs();
            System.out.println("✅ Created routes directory");
        }
        
        while (true) {
            System.out.println("\nOptions:");
            System.out.println("1. Generate new route");
            System.out.println("2. Generate all predefined routes");
            System.out.println("3. Exit");
            System.out.print("Choose option (1-3): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1" -> generateCustomRoute(scanner);
                case "2" -> generatePredefinedRoutes();
                case "3" -> {
                    System.out.println("👋 Goodbye!");
                    return;
                }
                default -> System.out.println("❌ Invalid choice. Please enter 1, 2, or 3.");
            }
        }
    }
    
    private static void generateCustomRoute(Scanner scanner) {
        System.out.println("\n📍 Generate Custom Route");
        System.out.println("======================");
        
        System.out.print("Enter route name (e.g., 'cumming_to_airport'): ");
        String routeName = scanner.nextLine().trim().toLowerCase().replace(" ", "_");
        
        System.out.print("Enter start location (e.g., 'Post Road, Cumming, GA'): ");
        String startLocation = scanner.nextLine().trim();
        
        System.out.print("Enter end location (e.g., 'Hartsfield-Jackson Airport, Atlanta, GA'): ");
        String endLocation = scanner.nextLine().trim();
        
        try {
            System.out.println("🌐 Calling OpenRouteService API...");
            String routeData = fetchRouteFromAPI(startLocation, endLocation);
            
            if (routeData != null) {
                String filename = routeName + ".json";
                saveRouteToFile(routeData, filename, routeName, startLocation, endLocation);
                System.out.println("✅ Route saved successfully to: " + filename);
            } else {
                System.out.println("❌ Failed to generate route. Please check your locations and try again.");
            }
        } catch (Exception e) {
            System.out.println("❌ Error generating route: " + e.getMessage());
        }
    }
    
    private static void generatePredefinedRoutes() {
        System.out.println("\n📋 Generating Predefined Routes");
        System.out.println("==============================");
        
        // Define the predefined routes with better geocodable addresses
        String[][] routes = {
            {"peachtree_south", "Peachtree Street, Midtown, Atlanta, GA", "Peachtree Street, Downtown, Atlanta, GA"},
            {"downtown_connector", "I-75, Atlanta, GA", "I-75, Downtown, Atlanta, GA"},
            {"ponce_de_leon", "Ponce de Leon Avenue, Atlanta, GA", "Ponce de Leon Avenue, Midtown, Atlanta, GA"},
            {"ga400_north", "GA-400, Sandy Springs, GA", "GA-400, Buckhead, Atlanta, GA"},
            {"north_ave_corridor", "North Avenue, Atlanta, GA", "North Avenue, West Atlanta, GA"},
            {"cumming_to_airport", "Cumming, GA", "Hartsfield Jackson Atlanta International Airport, GA"}
        };
        
        int successful = 0;
        int total = routes.length;
        
        for (String[] route : routes) {
            String routeName = route[0];
            String start = route[1];
            String end = route[2];
            
            System.out.println("\n🔄 Generating: " + routeName);
            System.out.println("   From: " + start);
            System.out.println("   To: " + end);
            
            try {
                Thread.sleep(1000); // Be nice to the API - 1 second between requests
                
                String routeData = fetchRouteFromAPI(start, end);
                if (routeData != null) {
                    String filename = routeName + ".json";
                    saveRouteToFile(routeData, filename, routeName, start, end);
                    System.out.println("   ✅ Saved to: " + filename);
                    successful++;
                } else {
                    System.out.println("   ❌ Failed to generate route");
                }
            } catch (Exception e) {
                System.out.println("   ❌ Error: " + e.getMessage());
            }
        }
        
        System.out.println("\n📊 Summary: " + successful + "/" + total + " routes generated successfully");
    }
    
    private static String fetchRouteFromAPI(String start, String end) {
        try {
            String apiKey = System.getenv("OPENROUTE_API_KEY");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                System.out.println("   ❌ OPENROUTE_API_KEY environment variable not set");
                System.out.println("   💡 Set it with: export OPENROUTE_API_KEY=your_api_key_here");
                return null;
            }
            
            // Use Nominatim to geocode addresses first
            double[] startCoords = geocodeAddress(start);
            double[] endCoords = geocodeAddress(end);
            
            if (startCoords == null || endCoords == null) {
                System.out.println("   ❌ Could not geocode addresses");
                return null;
            }
            
            // Build ORS request URL with API key (note: coordinates are lon,lat format)
            String url = String.format("%s?start=%f,%f&end=%f,%f", 
                ORS_API_BASE, startCoords[1], startCoords[0], endCoords[1], endCoords[0]);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/geo+json")
                .header("Authorization", apiKey)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                System.out.println("   ❌ API Error: " + response.statusCode() + " - " + response.body());
                return null;
            }
        } catch (Exception e) {
            System.out.println("   ❌ Network error: " + e.getMessage());
            return null;
        }
    }
    
    private static double[] geocodeAddress(String address) {
        try {
            // Clean up address for better geocoding
            String cleanAddress = address.trim();
            
            // Try the address as-is first
            double[] coords = tryGeocode(cleanAddress);
            if (coords != null) {
                return coords;
            }
            
            // If that fails, try just the city and state
            if (cleanAddress.contains(",")) {
                String[] parts = cleanAddress.split(",");
                if (parts.length >= 2) {
                    String cityState = parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
                    coords = tryGeocode(cityState);
                    if (coords != null) {
                        return coords;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static double[] tryGeocode(String address) {
        try {
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + encodedAddress + 
                        "&format=json&limit=1&countrycodes=us";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "TelematicsRouteGenerator/1.0")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode results = mapper.readTree(response.body());
                if (results.isArray() && results.size() > 0) {
                    JsonNode first = results.get(0);
                    double lat = first.get("lat").asDouble();
                    double lon = first.get("lon").asDouble();
                    System.out.println("   🗺️  Geocoded: " + address + " -> " + lat + ", " + lon);
                    
                    // Add a small delay to be respectful to Nominatim
                    Thread.sleep(500);
                    return new double[]{lat, lon};
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static void saveRouteToFile(String routeData, String filename, String routeName, 
                                      String startLocation, String endLocation) throws IOException {
        // Parse the ORS response and convert to our format
        JsonNode orsResponse = mapper.readTree(routeData);
        
        // Create our route file format
        ObjectNode routeFile = mapper.createObjectNode();
        routeFile.put("name", routeName);
        routeFile.put("description", startLocation + " → " + endLocation);
        routeFile.put("start_location", startLocation);
        routeFile.put("end_location", endLocation);
        
        ArrayNode waypoints = mapper.createArrayNode();
        
        // Extract coordinates from ORS GeoJSON response
        if (orsResponse.has("features") && orsResponse.get("features").isArray()) {
            JsonNode feature = orsResponse.get("features").get(0);
            if (feature.has("geometry") && feature.get("geometry").has("coordinates")) {
                JsonNode coordinates = feature.get("geometry").get("coordinates");
                
                // Sample every nth coordinate to avoid too many waypoints
                int sampleRate = Math.max(1, coordinates.size() / 15); // Max ~15 waypoints
                
                for (int i = 0; i < coordinates.size(); i += sampleRate) {
                    JsonNode coord = coordinates.get(i);
                    double lon = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    
                    ObjectNode waypoint = mapper.createObjectNode();
                    waypoint.put("latitude", lat);
                    waypoint.put("longitude", lon);
                    waypoint.put("street_name", guessStreetName(i, coordinates.size(), startLocation, endLocation));
                    waypoint.put("speed_limit", guessSpeedLimit(startLocation, endLocation));
                    waypoint.put("has_traffic_light", i % 4 == 0); // Every 4th point has traffic light
                    waypoint.put("traffic_control", i % 4 == 0 ? "traffic_light" : "none");
                    
                    waypoints.add(waypoint);
                }
                
                // Always include the last point
                if (coordinates.size() > 1) {
                    JsonNode lastCoord = coordinates.get(coordinates.size() - 1);
                    double lastLon = lastCoord.get(0).asDouble();
                    double lastLat = lastCoord.get(1).asDouble();
                    
                    ObjectNode lastWaypoint = mapper.createObjectNode();
                    lastWaypoint.put("latitude", lastLat);
                    lastWaypoint.put("longitude", lastLon);
                    lastWaypoint.put("street_name", "End: " + endLocation.split(",")[0]);
                    lastWaypoint.put("speed_limit", 25);
                    lastWaypoint.put("has_traffic_light", true);
                    lastWaypoint.put("traffic_control", "traffic_light");
                    
                    waypoints.add(lastWaypoint);
                }
            }
        }
        
        routeFile.set("waypoints", waypoints);
        
        // Save to file
        File routeFile_file = new File("src/main/resources/routes/" + filename);
        mapper.writerWithDefaultPrettyPrinter().writeValue(routeFile_file, routeFile);
    }
    
    private static String guessStreetName(int index, int total, String start, String end) {
        if (index == 0) return "Start: " + start.split(",")[0];
        if (index == total - 1) return "End: " + end.split(",")[0];
        
        // Simple heuristic based on location type
        if (start.contains("I-") || end.contains("I-")) return "Interstate";
        if (start.contains("GA-") || end.contains("GA-")) return "State Highway";
        return "Local Street";
    }
    
    private static int guessSpeedLimit(String start, String end) {
        if (start.contains("I-") || end.contains("I-")) return 70;
        if (start.contains("GA-400") || end.contains("GA-400")) return 65;
        if (start.contains("Airport") || end.contains("Airport")) return 45;
        return 35; // Default city street
    }
}