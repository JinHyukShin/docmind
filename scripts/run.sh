#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Running DocMind Application ===${NC}"
echo "Project root: $PROJECT_ROOT"

# Change to project root
cd "$PROJECT_ROOT"

# Check if JAR file exists
if ! ls build/libs/*.jar 1> /dev/null 2>&1; then
  echo -e "${RED}Error: No JAR file found in build/libs/${NC}"
  echo -e "${YELLOW}Please run: ./scripts/build.sh${NC}"
  exit 1
fi

# Get the latest built JAR
JAR_FILE=$(ls -t build/libs/*.jar | head -1)

echo -e "${YELLOW}Starting application with JAR: $(basename "$JAR_FILE")${NC}"

# Check if infrastructure is running
if ! docker ps | grep -q docmind-postgres; then
  echo -e "${YELLOW}Warning: PostgreSQL container is not running${NC}"
  echo -e "${YELLOW}Please run: ./scripts/start-infra.sh${NC}"
  read -p "Continue anyway? (y/n) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

# Run the application
echo -e "${GREEN}Application starting...${NC}"
java -jar "$JAR_FILE"
