#!/usr/bin/env bash
# Build and run the Admin Desktop app.
# Usage: ./run-desktop.sh [server-url]
#
# The server URL defaults to http://localhost:8080 (the API Gateway).
# Pass a URL to override, e.g.:  ./run-desktop.sh http://192.168.1.108:8080
set -e
cd "$(dirname "$0")"

SERVER_URL="${1:-http://localhost:8080}"

echo "Building Admin Desktop..."
gradle build -x test >/dev/null

echo "Starting Admin Desktop -> $SERVER_URL"
java -Datm.server="$SERVER_URL" -jar build/libs/admin-desktop-1.0.0.jar
