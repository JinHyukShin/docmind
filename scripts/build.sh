#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Building DocMind ===${NC}"
echo "Project root: $PROJECT_ROOT"

# Change to project root
cd "$PROJECT_ROOT"

# Check if gradlew exists
if [ ! -f "gradlew" ]; then
  echo -e "${RED}Error: gradlew not found in project root${NC}"
  exit 1
fi

# Make gradlew executable (important for Git Bash compatibility)
chmod +x gradlew

# Build with Gradle, excluding tests
echo -e "${YELLOW}Running: ./gradlew clean build -x test${NC}"
./gradlew clean build -x test

# Check if build was successful
if [ $? -eq 0 ]; then
  echo -e "${GREEN}=== Build Completed Successfully ===${NC}"

  # Find the built JAR file
  if ls build/libs/*.jar 1> /dev/null 2>&1; then
    JAR_FILE=$(ls -t build/libs/*.jar | head -1)
    echo -e "${GREEN}Built JAR: $(basename "$JAR_FILE")${NC}"
  fi
else
  echo -e "${RED}Build failed${NC}"
  exit 1
fi
