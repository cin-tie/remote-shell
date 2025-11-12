#!/bin/bash

# Remote Shell - Server Startup Script
# Location: scripts/run-server.sh

echo "=== Starting Remote Shell Server ==="

# Проверяем, скомпилирован ли проект
if [ ! -d "build" ]; then
    echo "❌ Build directory not found. Please run compile.sh first."
    exit 1
fi

# Запускаем сервер
echo "Starting server on port 8072..."
java -cp build csdev.server.ServerMain

echo "Server stopped."