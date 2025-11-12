#!/bin/bash

# Remote Shell - Compilation Script
# Location: scripts/compile.sh

echo "=== Compiling Remote Shell Application ==="

# Создаем директорию для скомпилированных классов
mkdir -p build

# Компилируем все Java файлы
echo "Compiling Java sources..."
javac -d build -sourcepath src $(find src -name "*.java")

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Compiled classes are in: build/"
else
    echo "Compilation failed!"
    exit 1
fi