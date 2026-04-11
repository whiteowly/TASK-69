#!/usr/bin/env bash
# Runs all unit and component tests (no external services required).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Unit & Component Tests ==="

echo "--- Backend Tests (Spring Boot + H2, no external DB) ---"
docker run --rm -v "$(pwd)/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn -q test -Dspring.profiles.active=test
echo "Backend tests: PASSED"

echo ""
echo "--- Frontend Component Tests (Vitest + Vue Test Utils) ---"
docker run --rm -v "$(pwd)/frontend":/app -w /app node:20-alpine sh -c "npm ci --ignore-scripts && npx vitest run"
echo "Frontend tests: PASSED"

echo ""
echo "=== All unit/component tests PASSED ==="
