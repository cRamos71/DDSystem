#!/bin/bash

echo "Compiling and starting RMI Backup Server..."
cd "$(dirname "$0")"

# Compile from src/ into out/
javac -d out src/**/*.java

# Run the Server class in package Server
java -cp out Server.ServerBackup