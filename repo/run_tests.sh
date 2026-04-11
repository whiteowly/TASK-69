#!/usr/bin/env bash
set -euo pipefail

echo "=== CROH Test Suite ==="
FAILURES=0

# Backend tests
echo ""
echo "--- Backend Tests ---"
cd "$(dirname "$0")/backend"
if docker run --rm -v "$(pwd)":/app -w /app maven:3.9-eclipse-temurin-17 mvn -q test -Dspring.profiles.active=test; then
  echo "Backend tests: PASSED"
else
  echo "Backend tests: FAILED"
  FAILURES=$((FAILURES + 1))
fi
cd ..

# Frontend tests
echo ""
echo "--- Frontend Tests ---"
cd "$(dirname "$0")/frontend"
if docker run --rm -v "$(pwd)":/app -w /app node:20-alpine sh -c "npm ci --ignore-scripts && npx vitest run"; then
  echo "Frontend tests: PASSED"
else
  echo "Frontend tests: FAILED"
  FAILURES=$((FAILURES + 1))
fi
cd ..

# E2E tests (requires running compose stack)
echo ""
echo "--- E2E Tests ---"
cd "$(dirname "$0")"
# Start the stack
docker compose up --build -d 2>/dev/null
# Wait for backend to be ready (up to 60s)
for i in $(seq 1 30); do
  if docker compose exec -T backend wget -qO- --timeout=2 http://localhost:8080/api/v1/auth/me 2>/dev/null | grep -q ""; then
    break
  fi
  sleep 2
done
NETWORK=$(docker network ls --format '{{.Name}}' | grep repo | head -1)
if docker run --rm --network "$NETWORK" -v "$(pwd)/e2e":/e2e -w /e2e -e BASE_URL=http://frontend:3000 node:20-alpine sh -c "npm install --ignore-scripts 2>/dev/null && npx playwright test --project=api --reporter=line 2>&1"; then
  echo "E2E tests: PASSED"
else
  echo "E2E tests: FAILED"
  FAILURES=$((FAILURES + 1))
fi
docker compose down 2>/dev/null

echo ""
if [ "$FAILURES" -gt 0 ]; then
  echo "=== $FAILURES test suite(s) FAILED ==="
  exit 1
else
  echo "=== All test suites PASSED ==="
fi
