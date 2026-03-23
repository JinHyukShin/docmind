#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Starting DocMind Infrastructure ===${NC}"
echo "Project root: $PROJECT_ROOT"

# Change to project root
cd "$PROJECT_ROOT"

# Check if docker-compose.yml exists
if [ ! -f "docker/docker-compose.yml" ]; then
  echo -e "${RED}Error: docker/docker-compose.yml not found${NC}"
  exit 1
fi

# Start Docker Compose services
echo -e "${YELLOW}Starting Docker Compose services...${NC}"
docker-compose -f docker/docker-compose.yml up -d

# Wait for services to be healthy
echo -e "${YELLOW}Waiting for services to be healthy...${NC}"

MAX_ATTEMPTS=30
ATTEMPT=0

# Check PostgreSQL + pgvector
echo -e "${YELLOW}Checking PostgreSQL...${NC}"
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if docker exec docmind-postgres pg_isready -U docmind -d docmind >/dev/null 2>&1; then
    echo -e "${GREEN}PostgreSQL is ready${NC}"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "Attempt $ATTEMPT/$MAX_ATTEMPTS - PostgreSQL not ready yet..."
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo -e "${RED}PostgreSQL failed to start${NC}"
  exit 1
fi

# Check Redis
ATTEMPT=0
echo -e "${YELLOW}Checking Redis...${NC}"
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if docker exec docmind-redis redis-cli ping >/dev/null 2>&1; then
    echo -e "${GREEN}Redis is ready${NC}"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "Attempt $ATTEMPT/$MAX_ATTEMPTS - Redis not ready yet..."
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo -e "${RED}Redis failed to start${NC}"
  exit 1
fi

# Check MinIO
ATTEMPT=0
echo -e "${YELLOW}Checking MinIO...${NC}"
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
    echo -e "${GREEN}MinIO is ready${NC}"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "Attempt $ATTEMPT/$MAX_ATTEMPTS - MinIO not ready yet..."
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo -e "${YELLOW}Warning: MinIO health check may not be configured${NC}"
fi

echo -e "${GREEN}=== All Infrastructure Services Started Successfully ===${NC}"
echo -e "${GREEN}PostgreSQL: localhost:5432${NC}"
echo -e "${GREEN}Redis: localhost:6379${NC}"
echo -e "${GREEN}MinIO: localhost:9000${NC}"
