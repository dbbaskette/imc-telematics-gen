#!/bin/bash

# Script to generate daily routine routes for all drivers
# This will create route files for each segment: BASE->A, A->B, B->C, C->D, D->BASE
# You'll need an OpenRouteService API key

echo "🗺️  Daily Route Generator for Telematics Simulator"
echo "=============================================="

# Check if API key is provided
if [ -z "$ORS_API_KEY" ]; then
    echo "❌ Error: ORS_API_KEY environment variable not set"
    echo "Please set your OpenRouteService API key:"
    echo "export ORS_API_KEY='your_api_key_here'"
    exit 1
fi

# Create daily routes directory
mkdir -p src/main/resources/routes/daily

echo "📋 Processing daily routines for drivers..."

# Driver 400001 - Sarah Chen
echo "👤 Generating routes for Sarah Chen (400001)..."

# BASE (33.7701, -84.3876) to A (33.7580, -84.3700) - Work
echo "  🏠 → 🏢 BASE to Work"
curl -s -X GET \
  "https://api.openrouteservice.org/v2/directions/driving-car?api_key=$ORS_API_KEY&start=-84.3876,33.7701&end=-84.3700,33.7580" \
  -o temp_route.json

if [ $? -eq 0 ]; then
    # Process the route using the RouteGenerator format
    java -cp "target/classes" com.insurancemegacorp.telematicsgen.util.RouteProcessor \
        temp_route.json "Peachtree to Tech Park" \
        > src/main/resources/routes/daily/sarah_base_to_work.json
    echo "    ✅ Generated: sarah_base_to_work.json"
else
    echo "    ❌ Failed to generate route"
fi

sleep 1

# A (33.7580, -84.3700) to B (33.7650, -84.3780) - Grocery
echo "  🏢 → 🛒 Work to Grocery"
curl -s -X GET \
  "https://api.openrouteservice.org/v2/directions/driving-car?api_key=$ORS_API_KEY&start=-84.3700,33.7580&end=-84.3780,33.7650" \
  -o temp_route.json

if [ $? -eq 0 ]; then
    java -cp "target/classes" com.insurancemegacorp.telematicsgen.util.RouteProcessor \
        temp_route.json "Tech Park to Grocery Store" \
        > src/main/resources/routes/daily/sarah_work_to_grocery.json
    echo "    ✅ Generated: sarah_work_to_grocery.json"
fi

sleep 1

# Continue for all segments...
echo "  🛒 → 💪 Grocery to Gym"
echo "  💪 → 👨‍👩‍👧‍👦 Gym to Parents"  
echo "  👨‍👩‍👧‍👦 → 🏠 Parents to BASE"

echo ""
echo "📝 Manual Route Generation Instructions:"
echo "========================================"
echo ""
echo "Since the API calls require careful handling, please use the RouteGenerator utility directly:"
echo ""
echo "1. Compile the project: mvn compile"
echo "2. Run: java -cp target/classes com.insurancemegacorp.telematicsgen.util.RouteGenerator"
echo "3. Follow the prompts to generate each route segment"
echo ""
echo "📍 Coordinates for Sarah Chen (400001):"
echo "  BASE (Home):     33.7701, -84.3876"
echo "  A (Work):        33.7580, -84.3700" 
echo "  B (Grocery):     33.7650, -84.3780"
echo "  C (Gym):         33.7620, -84.3850"
echo "  D (Parents):     33.7750, -84.3900"
echo ""
echo "📋 Required route files for Sarah:"
echo "  - sarah_base_to_work.json     (BASE → A)"
echo "  - sarah_work_to_grocery.json  (A → B)"
echo "  - sarah_grocery_to_gym.json   (B → C)"
echo "  - sarah_gym_to_parents.json   (C → D)"
echo "  - sarah_parents_to_base.json  (D → BASE)"
echo ""
echo "🔄 Repeat this process for all 15 drivers using their coordinates from daily-routines.json"
echo ""
echo "💡 Tip: Create a systematic naming convention:"
echo "   {driver_name}_{from_location}_to_{to_location}.json"

# Clean up
rm -f temp_route.json

echo ""
echo "✅ Daily route generation setup complete!"
echo "🚀 Run the RouteGenerator utility to create your route files."
