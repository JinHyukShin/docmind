#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Stopping DocMind Infrastructure ===${NC}"
echo "Project root: $PROJECT_ROOT"

# Change to project root
cd "$PROJECT_ROOT"

# Check if docker-compose.yml exists
if [ ! -f "docker/docker-compose.yml" ]; then
  echo -e "${RED}Error: docker/docker-compose.yml not found${NC}"
  exit 1
fi

# Stop Docker Compose services
echo -e "${YELLOW}Stopping Docker Compose services...${NC}"
docker-compose -f docker/docker-compose.yml down

echo -e "${GREEN}=== Infrastructure Stopped Successfully ===${NC}"
