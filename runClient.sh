#!/bin/bash

echo "Compiling and starting RMI Client..."
cd "$(dirname "$0")"

# Compile from src/ into out/
javac -d out src/**/*.java

# Run the Client class in package Client
java -cp out Client.Client