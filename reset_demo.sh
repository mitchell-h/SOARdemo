#!/bin/bash

# ============================================================
# SOAR Demo Reset Script
# This script wipes all containers, images, and local data
# to ensure a complete reset of the demo environment.
# ============================================================

# Ensure we're in the project root
if [ ! -f "docker-compose.yml" ]; then
    echo "Error: docker-compose.yml not found. Please run this script from the project root."
    exit 1
fi

echo "--- Resetting SOAR Demo ---"

# Step 1: docker compose down
# -v: removes anonymous volumes
# --rmi all: removes all images used by services
echo "Stopping and removing Docker containers, volumes, and images..."
docker compose down -v --rmi all --remove-orphans

# Step 2: Remove local data files
# These are the JSON flat files used by the services.
# We exclude src/ and .vscode/ to avoid deleting source code or config.
echo "Deleting generated JSON data files..."
find . -name "*.json" \
    -not -path "*/src/*" \
    -not -path "*/.vscode/*" \
    -not -path "*/.gradle/*" \
    -delete

# Step 3: Clean up Gradle build artifacts
echo "Cleaning Gradle build directories..."
if [ -f "./gradlew" ]; then
    ./gradlew clean
fi
rm -rf build/ .gradle/ **/build/

# Step 4: Final cleanup
echo "Performing final Docker cleanup (unused networks/images)..."
docker network prune -f

echo "--- Reset Complete ---"
