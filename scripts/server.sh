#!/bin/bash

echo "=== Starting Remote Shell Server ==="

if [ ! -d "build" ]; then
    echo "Build directory not found. Please run compile.sh first."
    exit 1
fi

PASSWORD=""
SERVER_IP=$(hostname -I | awk '{print $1}')

while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--pass)
            PASSWORD="$2"
            shift 2
            ;;
        -h|--host)
            SERVER_IP="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [-p password] [-h external_ip]"
            exit 1
            ;;
    esac
done

echo "Using RMI hostname: $SERVER_IP"
echo "Starting server on port 8072 (TCP/UDP) and 8073 (RMI)..."

JAVA_OPTS="-Djava.rmi.server.hostname=$SERVER_IP"

if [ -n "$PASSWORD" ]; then
    echo "Server Password: $PASSWORD"
    java $JAVA_OPTS -cp build csdev.server.ServerMain "$PASSWORD"
else
    echo "No password provided - starting without authentication"
    java $JAVA_OPTS -cp build csdev.server.ServerMain
fi

echo "Server stopped."
