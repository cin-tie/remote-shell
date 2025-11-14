#!/bin/bash

echo "=== Starting Remote Shell Server ==="

if [ ! -d "build" ]; then
    echo "❌ Build directory not found. Please run compile.sh first."
    exit 1
fi

echo "Starting server on port 8072..."
java -cp build csdev.server.ServerMain

echo "Server stopped."