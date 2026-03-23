#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${GREEN}=== Initializing DocMind Database ===${NC}"
echo "Project root: $PROJECT_ROOT"

# Change to project root
cd "$PROJECT_ROOT"

# Check if migration directory exists
if [ ! -d "database/migration" ]; then
  echo -e "${RED}Error: database/migration directory not found${NC}"
  exit 1
fi

# Check if PostgreSQL container is running
if ! docker ps | grep -q docmind-postgres; then
  echo -e "${RED}Error: PostgreSQL container is not running${NC}"
  echo -e "${YELLOW}Please run: ./scripts/start-infra.sh${NC}"
  exit 1
fi

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
MAX_ATTEMPTS=30
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if docker exec docmind-postgres pg_isready -U docmind -d docmind >/dev/null 2>&1; then
    echo -e "${GREEN}PostgreSQL is ready${NC}"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "Attempt $ATTEMPT/$MAX_ATTEMPTS..."
  sleep 1
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo -e "${RED}PostgreSQL failed to become ready${NC}"
  exit 1
fi

# Execute migration files
echo -e "${YELLOW}Running database migrations...${NC}"

# Find and sort migration files
MIGRATION_FILES=$(find database/migration -name "*.sql" | sort)

if [ -z "$MIGRATION_FILES" ]; then
  echo -e "${YELLOW}No migration files found${NC}"
else
  for SQL_FILE in $MIGRATION_FILES; do
    echo -e "${YELLOW}Executing: $SQL_FILE${NC}"
    docker exec -i docmind-postgres psql -U docmind -d docmind < "$SQL_FILE"
    echo -e "${GREEN}✓ $SQL_FILE completed${NC}"
  done
fi

echo -e "${GREEN}=== Database Initialization Completed Successfully ===${NC}"
