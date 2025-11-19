#!/bin/bash

echo "=== Starting Remote Shell Client ==="

if [ ! -d "build" ]; then
    echo "Build directory not found. Please run compile.sh first."
    exit 1
fi

USER_NICK=""
USER_FULL=""
HOST="localhost"
PASS=""
PROTOCOL="tcp"

while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--user)
            USER_NICK="$2"
            USER_FULL="$3"
            shift 3
            ;;
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -p|--pass)
            PASS="$2"
            shift 2
            ;;
        -pr|--protocol)
            PROTOCOL="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [-u <nick> \"<full name>\"] [-h host] [-p password]"
            echo "Available parameters:"
            echo "    -u <nick> \"<full name>\""
            echo "    -h hostname"
            echo "    -p password"
            echo "    -pr tcp|udp (default: tcp)"
            exit 1
            ;;
    esac
done

if [ -z "$USER_NICK" ] || [ -z "$USER_FULL" ]; then
    IP=$(ip -4 addr show scope global | awk '/inet / {print $2}' | cut -d/ -f1 | head -n1)
    USER_NICK="guest_from_$IP"
    USER_FULL="Guest User from $IP"
    echo "No user specified - using auto-generated: $USER_NICK"
fi

echo "Connecting to $HOST via $PROTOCOL as $USER_NICK ($USER_FULL)..."
if [ -n "$PASS" ]; then
    echo "Using password authentication"
else
    echo "No password provided - connection may fail if server requires authentication"
fi

if [ "$PROTOCOL" = "udp" ]; then
    java -cp build csdev.client.UdpClientMain "$USER_NICK" "$USER_FULL" "$HOST" "$PASS"
else
    java -cp build csdev.client.TcpClientMain "$USER_NICK" "$USER_FULL" "$HOST" "$PASS"
fi