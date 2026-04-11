#!/bin/sh
set -e

SECRETS_DIR="/run/secrets"

# Only generate if not already present (idempotent)
if [ -f "$SECRETS_DIR/db_root_password" ]; then
  echo "Secrets already generated, skipping."
  exit 0
fi

echo "Generating runtime secrets..."

# Generate random values for all secrets
cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1 > "$SECRETS_DIR/db_root_password"
printf "croh_%s" "$(cat /dev/urandom | tr -dc 'a-z0-9' | fold -w 8 | head -n 1)" > "$SECRETS_DIR/db_name"
printf "u_%s" "$(cat /dev/urandom | tr -dc 'a-z0-9' | fold -w 12 | head -n 1)" > "$SECRETS_DIR/db_username"
cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1 > "$SECRETS_DIR/db_password"
cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1 > "$SECRETS_DIR/encryption_key"

chmod 444 "$SECRETS_DIR"/*

echo "Secrets generated successfully."
