#!/bin/bash


echo "=== Starting Remote Shell Client ==="

if [ ! -d "build" ]; then
    echo "❌ Build directory not found. Please run compile.sh first."
    exit 1
fi

if [ $# -lt 3 ]; then
    echo "Usage: $0 <user_nickname> <user_full_name> <host> [password]"
    echo "Example: $0 john \"John Doe\" localhost\ password"
    exit 1
fi

USER_NICK="$1"
USER_FULL="$2"
HOST="$3"
PASS="${4:""}"

echo "Connecting to $HOST as $USER_NICK ($USER_FULL)..."

java -cp build csdev.client.ClientMain "$USER_NICK" "$USER_FULL" "$HOST" "$PASS"