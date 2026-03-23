#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Running DocMind Tests ===${NC}"
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

# Run tests with Gradle
echo -e "${YELLOW}Running: ./gradlew test${NC}"
./gradlew test

if [ $? -eq 0 ]; then
  echo -e "${GREEN}=== All Tests Passed Successfully ===${NC}"
else
  echo -e "${RED}Some tests failed${NC}"
  exit 1
fi
