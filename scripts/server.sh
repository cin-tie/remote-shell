#!/bin/bash

echo "=== Starting Remote Shell Server ==="

if [ ! -d "build" ]; then
    echo "Build directory not found. Please run compile.sh first."
    exit 1
fi

PASSWORD=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--pass)
            PASSWORD="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [-p password]"
            exit 1
            ;;
    esac
done

echo "Starting server on port 8072..."

if [ -n "$PASSWORD" ]; then
    echo "Server Password: $PASSWORD"
    java -cp build csdev.server.ServerMain "$PASSWORD"
else
    echo "No password provided - starting without authentication"
    java -cp build csdev.server.ServerMain
fi

echo "Server stopped."