#!/bin/bash

echo "=== Starting Remote Shell Server ==="

if [ ! -d "build" ]; then
    echo "❌ Build directory not found. Please run compile.sh first."
    exit 1
fi

echo "Starting server on port 8072..."

if [ $# -eq 1 ]; then
    echo "Server Password: $1"
    java -cp build csdev.server.ServerMain "$1"
else
    echo "No password provided - starting without authentication"
    java -cp build csdev.server.ServerMain
fi

echo "Server stopped."