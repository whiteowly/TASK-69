#!/usr/bin/env bash
# Runs all API-level tests: backend integration tests + Playwright API E2E tests.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== API Tests ==="

echo "--- Backend Integration Tests (Spring Boot + H2) ---"
docker run --rm -v "$(pwd)/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn -q test -Dspring.profiles.active=test
echo "Backend integration tests: PASSED"

echo ""
echo "--- Playwright API E2E Tests (full Docker stack) ---"
docker compose -f docker-compose.yml up --build -d 2>/dev/null
for i in $(seq 1 30); do
  if docker compose -f docker-compose.yml exec -T backend wget -qO- --timeout=2 http://localhost:8080/api/v1/auth/me 2>/dev/null | grep -q ""; then break; fi
  sleep 2
done
NETWORK=$(docker network ls --format '{{.Name}}' | grep repo | head -1)
docker run --rm --network "$NETWORK" -v "$(pwd)/e2e":/e2e -w /e2e -e BASE_URL=http://frontend:3000 node:20-alpine sh -c "npm install --ignore-scripts 2>/dev/null && npx playwright test --project=api --reporter=line"
echo "Playwright API E2E tests: PASSED"
docker compose -f docker-compose.yml down 2>/dev/null

echo ""
echo "=== All API tests PASSED ==="
