#!/bin/bash

echo "Compiling and starting RMI Client..."
cd "$(dirname "$0")"

# Caminhos dos JARs
CP="/Users/mlmtpinto/IdeaProjects/DDSystem/amqp-client-5.25.0.jar"
CP="$CP:/Users/mlmtpinto/IdeaProjects/DDSystem/slf4j-api-1.7.36.jar"
CP="$CP:/Users/mlmtpinto/IdeaProjects/DDSystem/slf4j-simple-1.7.36.jar"

# Compilar fontes
javac -d out -cp "$CP" src/**/*.java

# Rodar cliente
java -cp "$CP:out" Client.Client