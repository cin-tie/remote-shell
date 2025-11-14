#!/bin/bash

echo "=== Compiling Remote Shell Application ==="

mkdir -p build

echo "Compiling Java sources..."
javac -d build -sourcepath src $(find src -name "*.java")

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Compiled classes are in: build/"
else
    echo "Compilation failed!"
    exit 1
fi