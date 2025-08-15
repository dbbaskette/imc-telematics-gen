#!/usr/bin/env python3
"""
Script to generate remaining daily route files for all drivers.
Creates route JSON files based on the daily-routines.json configuration.
"""

import json
import os
from pathlib import Path

def get_atlanta_street_name(lat, lon, waypoint_index, total_waypoints, route_name):
    """Get realistic Atlanta metro street name including suburbs and highways."""
    # Comprehensive Atlanta metro area streets including suburbs and highways
    atlanta_streets = [
        # MAJOR INTERSTATES AND HIGHWAYS
        "I-75 North", "I-75 South", "I-85 North", "I-85 South", 
        "I-20 East", "I-20 West", "I-285 (Perimeter)", "I-575 North",
        "I-675 South", "I-985 North", "GA-400 North", "GA-400 South",
        
        # STATE HIGHWAYS
        "Highway 9 (Roswell Road)", "Highway 92", "Highway 141 (Peachtree Rd)",
        "Highway 120", "Highway 124", "Highway 316", "Highway 78",
        "Highway 138", "Highway 5", "Highway 42", "Highway 85",
        
        # MAJOR SUBURBAN ARTERIALS - NORTH (Cumming, Duluth, Alpharetta)
        "Cumming City Beach Road", "Lanier Islands Parkway", "Browns Bridge Road",
        "Peachtree Parkway", "Windward Parkway", "Old Milton Parkway",
        "Duluth Highway", "Pleasant Hill Road", "Sugarloaf Parkway", 
        "Satellite Boulevard", "Steve Reynolds Boulevard", "Abbotts Bridge Road",
        "McGinnis Ferry Road", "Old Peachtree Road", "Spalding Drive",
        
        # GWINNETT COUNTY 
        "Buford Highway", "Lawrenceville Highway", "Jimmy Carter Boulevard",
        "Indian Trail Road", "Lilburn Stone Mountain Road", "Scenic Highway",
        "Club Drive", "Beaver Ruin Road", "Rockbridge Road", "Stone Mountain Highway",
        
        # COBB COUNTY
        "Cobb Parkway", "Marietta Highway", "Powder Springs Road", 
        "Dallas Highway", "Austell Road", "Veterans Memorial Highway",
        "Canton Road", "Roswell Road", "Johnson Ferry Road", "Lower Roswell Road",
        
        # FORSYTH COUNTY (Cumming area)
        "Bethelview Road", "Castleberry Road", "Keith Bridge Road", "Post Road",
        "Dahlonega Highway", "McFarland Parkway", "Pilgrim Mill Road",
        "Union Hill Road", "Spot Road", "Samples Road", "Drew Campground Road",
        
        # DEKALB COUNTY
        "Memorial Drive", "Ponce de Leon Avenue", "North Decatur Road",
        "Scott Boulevard", "Clairmont Road", "LaVista Road", "Briarcliff Road",
        "Chamblee Tucker Road", "Northlake Parkway", "Henderson Mill Road",
        
        # FULTON COUNTY SUBURBS
        "Roswell Road", "Holcomb Bridge Road", "Mansell Road", "Kimball Bridge Road",
        "Old Alabama Road", "Jones Bridge Road", "Webb Bridge Road",
        "Haynes Bridge Road", "Crossville Road", "Union Hill Road",
        
        # ATLANTA CITY - Major Peachtree variants
        "Peachtree Street NE", "Peachtree Street NW", "Peachtree Road", 
        "Peachtree Industrial Blvd", "Peachtree Dunwoody Road", "West Peachtree Street",
        
        # ATLANTA CITY - Major north-south arterials
        "Piedmont Avenue", "Spring Street", "Northside Drive", "Monroe Drive", 
        "North Highland Avenue", "Moreland Avenue", "Boulevard", "Candler Road",
        "Stone Mountain Freeway", "Memorial Drive", "Glenwood Avenue",
        
        # ATLANTA CITY - Major east-west routes
        "North Avenue", "Ponce de Leon Avenue", "Virginia Avenue", "Freedom Parkway",
        "Ralph McGill Boulevard", "Auburn Avenue", "Edgewood Avenue", "DeKalb Avenue",
        "Decatur Street", "Marietta Street", "Northside Parkway", "Collier Road",
        
        # ATLANTA CITY - Numbered streets
        "10th Street", "11th Street", "12th Street", "13th Street", "14th Street", 
        "15th Street", "16th Street", "17th Street", "18th Street", "19th Street",
        "5th Street", "6th Street", "7th Street", "8th Street", "9th Street",
        
        # MIDTOWN/BUCKHEAD
        "Juniper Street", "Cypress Street", "Myrtle Street", "Pine Street", 
        "Charles Allen Drive", "Argonne Avenue", "Piedmont Circle", "Ansley Mall", 
        "Monroe Circle", "Penn Avenue", "Glen Iris Drive", "Greenwood Avenue",
        "West Paces Ferry Road", "Howell Mill Road", "Collier Road", "Peachtree Battle Avenue",
        "Habersham Road", "Pharr Road", "Lenox Road", "Piedmont Road",
        
        # SUBURBAN SHOPPING/BUSINESS DISTRICTS
        "Town Center Boulevard", "Northpoint Parkway", "Mall of Georgia Boulevard",
        "Sugarloaf Mills Circle", "Perimeter Center East", "Perimeter Center West",
        "Cumberland Parkway", "Circle 75 Parkway", "Windy Hill Road",
        
        # LAKE LANIER AREA (Cumming)
        "Lake Lanier Islands Parkway", "Friendship Road", "Lakeshore Drive",
        "Aqualand Drive", "Holiday Road", "Sawnee Avenue", "Atlanta Highway",
        
        # RESIDENTIAL SUBURBS
        "Oakdale Road", "Briarcliff Road", "LaVista Road", "Clairmont Road", 
        "Lindbergh Drive", "Cheshire Bridge Road", "Scott Boulevard", 
        "Druid Hills Road", "Emory Road", "Clifton Road", "North Druid Hills Road",
        
        # SUBURBAN LOCAL STREETS
        "Beverly Road", "Rock Springs Road", "Powers Ferry Road", "Riverside Drive", 
        "Arden Road", "Broadland Road", "Westminster Drive", "West Wesley Road", 
        "Tuxedo Road", "Valley Road", "Johnson Ferry Road", "Spalding Drive"
    ]
    
    # Select street based on position in route and coordinates
    if waypoint_index == 0:
        return "Residential Driveway"
    elif waypoint_index == total_waypoints - 1:
        return "Destination Parking"
    else:
        # Better hash for more even distribution
        import hashlib
        hash_input = f"{route_name}_{lat:.6f}_{lon:.6f}_{waypoint_index}"
        hash_value = int(hashlib.md5(hash_input.encode()).hexdigest()[:8], 16)
        street_index = hash_value % len(atlanta_streets)
        return atlanta_streets[street_index]

def interpolate_route(start_lat, start_lon, end_lat, end_lon, route_name, description):
    """Generate a detailed route with many waypoints following Atlanta street patterns."""
    waypoints = []
    
    # Calculate distance to determine number of waypoints
    lat_diff = abs(end_lat - start_lat)
    lon_diff = abs(end_lon - start_lon)
    distance = (lat_diff + lon_diff) * 69  # Rough miles estimate
    
    # More waypoints for longer routes (aim for ~1 waypoint per 0.1 mile)
    num_waypoints = max(15, min(40, int(distance * 10)))
    
    for i in range(num_waypoints):
        # More realistic path following - not just linear interpolation
        progress = i / (num_waypoints - 1)
        
        # Add some realistic path variation (following street grid patterns)
        if i == 0:
            lat, lon = start_lat, start_lon
        elif i == num_waypoints - 1:
            lat, lon = end_lat, end_lon
        else:
            # Create a more realistic path with turns and street following
            base_lat = start_lat + (end_lat - start_lat) * progress
            base_lon = start_lon + (end_lon - start_lon) * progress
            
            # Add small variations to simulate following streets
            street_variation = 0.002  # About 200m variation
            lat_offset = (hash(f"{route_name}_{i}_lat") % 1000 - 500) / 1000000 * street_variation
            lon_offset = (hash(f"{route_name}_{i}_lon") % 1000 - 500) / 1000000 * street_variation
            
            lat = base_lat + lat_offset
            lon = base_lon + lon_offset
        
        # Determine speed limit and traffic control based on waypoint position and route type
        street_name = get_atlanta_street_name(lat, lon, i, num_waypoints, route_name)
        
        if i == 0 or i == num_waypoints - 1:
            speed_limit = 15  # Parking areas
            traffic_control = "none"
        elif "I-" in street_name or "Highway" in street_name or "GA-400" in street_name:
            speed_limit = 70 if "I-" in street_name else 55  # Interstate vs highway speeds
            traffic_control = "none"  # Highways don't have traffic lights
        elif "Parkway" in street_name or "Boulevard" in street_name:
            speed_limit = 45  # Major suburban arterials
            traffic_control = "traffic_light" if i % 6 == 0 else "none"
        elif i < 3 or i > num_waypoints - 4:
            speed_limit = 25  # Residential/local streets
            traffic_control = "stop_sign" if i % 3 == 0 else "none"
        elif progress > 0.2 and progress < 0.8:
            speed_limit = 35 if "Street" in street_name else 45  # City vs arterial roads
            traffic_control = "traffic_light" if i % 4 == 0 else "none"
        else:
            speed_limit = 35  # Regular city streets
            traffic_control = "traffic_light" if i % 5 == 0 else "yield" if i % 7 == 0 else "none"
        
        waypoints.append({
            "latitude": round(lat, 6),
            "longitude": round(lon, 6),
            "street_name": street_name,
            "speed_limit": speed_limit,
            "has_traffic_light": traffic_control == "traffic_light",
            "traffic_control": traffic_control
        })
    
    return {
        "name": route_name,
        "description": description,
        "start_location": f"{start_lat}, {start_lon}",
        "end_location": f"{end_lat}, {end_lon}",
        "waypoints": waypoints
    }

def generate_driver_routes(driver_data):
    """Generate all 5 route segments for a driver."""
    driver_id = driver_data["driver_id"]
    driver_name = driver_data["driver_name"].lower().replace(" ", "_")
    base_loc = driver_data["base_location"]
    remote_locs = {loc["id"]: loc for loc in driver_data["remote_locations"]}
    
    routes = []
    locations = ["BASE"] + driver_data["standard_sequence"] + ["BASE"]
    
    for i in range(len(locations) - 1):
        from_loc = locations[i]
        to_loc = locations[i + 1]
        
        # Get coordinates
        if from_loc == "BASE":
            from_coords = (base_loc["latitude"], base_loc["longitude"])
            from_name = "base"
        else:
            from_coords = (remote_locs[from_loc]["latitude"], remote_locs[from_loc]["longitude"])
            from_name = remote_locs[from_loc]["name"].lower().replace(" ", "_").replace("-", "_")
            
        if to_loc == "BASE":
            to_coords = (base_loc["latitude"], base_loc["longitude"])
            to_name = "base"
        else:
            to_coords = (remote_locs[to_loc]["latitude"], remote_locs[to_loc]["longitude"])
            to_name = remote_locs[to_loc]["name"].lower().replace(" ", "_").replace("-", "_")
        
        # Generate route
        route_name = f"{driver_name}_{from_name}_to_{to_name}"
        description = f"{driver_data['driver_name']}: {from_name.title()} → {to_name.title()}"
        
        route = interpolate_route(
            from_coords[0], from_coords[1],
            to_coords[0], to_coords[1],
            route_name, description
        )
        
        routes.append((route_name, route))
    
    return routes

def main():
    # Load daily routines configuration
    with open('src/main/resources/daily-routines.json', 'r') as f:
        config = json.load(f)
    
    # Create routes directory
    os.makedirs('src/main/resources/routes/daily', exist_ok=True)
    
    generated_count = 0
    
    # Generate routes for each driver
    for driver_data in config["daily_routines"]:
        driver_name = driver_data["driver_name"]
        print(f"Generating routes for {driver_name}...")
        
        routes = generate_driver_routes(driver_data)
        
        for route_name, route_data in routes:
            filename = f"src/main/resources/routes/daily/{route_name}.json"
            
            # Skip if file already exists
            if os.path.exists(filename):
                print(f"  ✓ {route_name}.json (already exists)")
                continue
                
            # Write route file
            with open(filename, 'w') as f:
                json.dump(route_data, f, indent=2)
            
            print(f"  ✓ Generated {route_name}.json")
            generated_count += 1
    
    print(f"\n🚗 Generated {generated_count} new route files!")
    print("📁 All routes saved to: src/main/resources/routes/daily/")

if __name__ == "__main__":
    main()
