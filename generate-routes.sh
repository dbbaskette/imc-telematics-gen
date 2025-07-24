#!/bin/bash

echo "🚗 Telematics Route Generator"
echo "============================"
echo ""

# Check if API key is set
if [ -z "$OPENROUTE_API_KEY" ]; then
    echo "⚠️  OpenRouteService API key not found in environment."
    echo ""
    echo "Please set your API key using one of these methods:"
    echo "1. Set for this session: export OPENROUTE_API_KEY=your_api_key_here"
    echo "2. Add to ~/.bashrc or ~/.zshrc: echo 'export OPENROUTE_API_KEY=your_api_key_here' >> ~/.bashrc"
    echo ""
    read -p "Enter your OpenRouteService API key now (or press Enter to exit): " api_key
    
    if [ -z "$api_key" ]; then
        echo "❌ No API key provided, exiting."
        exit 1
    fi
    
    export OPENROUTE_API_KEY="$api_key"
    echo "✅ API key set for this session"
fi

echo "This script will compile and run the RouteGenerator utility."
echo "Make sure you have internet connection for API calls."
echo ""

# Compile the project first
echo "📦 Compiling project..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    
    # Run the RouteGenerator
    echo "🚀 Starting RouteGenerator..."
    mvn exec:java -Dexec.mainClass="com.insurancemegacorp.telematicsgen.util.RouteGenerator" -Dexec.classpathScope="compile" -q
else
    echo "❌ Compilation failed"
    exit 1
fi