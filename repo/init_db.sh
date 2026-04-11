#!/usr/bin/env bash
set -euo pipefail

echo "=== CROH Database Initialization ==="

# Ensure compose infrastructure is up (builds images if needed)
docker compose up --build -d bootstrap db

echo "Waiting for database to be healthy..."
until docker compose ps db --format '{{.Status}}' 2>/dev/null | grep -q "(healthy)"; do
  sleep 2
done

echo "Database is ready."

# Start backend to run Flyway migrations
echo "Running Flyway migrations via backend..."
docker compose up --build -d backend

echo "Waiting for backend to complete startup..."
for i in $(seq 1 90); do
  if docker compose logs backend 2>&1 | grep -q "Started CrohApplication"; then
    echo "Backend started, migrations complete."
    break
  fi
  if docker compose ps backend --format '{{.Status}}' 2>/dev/null | grep -q "Exited"; then
    echo "ERROR: Backend exited unexpectedly."
    docker compose logs backend 2>&1 | tail -20
    exit 1
  fi
  if [ "$i" -eq 90 ]; then
    echo "ERROR: Backend failed to start within 90 seconds."
    docker compose logs backend 2>&1 | tail -20
    exit 1
  fi
  sleep 1
done

echo "=== Database initialization complete ==="
