#!/bin/bash
# Start the full banking stack (RMI core server + API gateway).
#
#   rmi-server  : HTTP 8081 + RMI registry 1099  (business logic + H2 database)
#   api-gateway : HTTP 8080                     (REST/WebSocket front for clients)
#
# The gateway answers UDP discovery probes on 45560, so mobile clients find it
# on whatever LAN address DHCP handed out - nothing needs to be recompiled when
# the IP changes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

RMI_JAR="rmi-server/build/libs/rmi-server-2.0.0.jar"
GATEWAY_JAR="api-gateway/build/libs/api-gateway-1.0.0.jar"
DISCOVERY_PORT=45560

find_gradle() {
    local cached
    cached=$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.2-bin/*/gradle-8.2/bin/gradle 2>/dev/null | head -1)
    if [ -n "$cached" ]; then
        echo "$cached"
    elif command -v gradle >/dev/null 2>&1; then
        command -v gradle
    else
        echo ""
    fi
}

build_jar() {
    local module="$1" jar="$2"
    if [ -f "$jar" ] && [ -z "$(find "$module/src" -newer "$jar" -name '*.java' -print -quit 2>/dev/null)" ]; then
        return 0
    fi
    local gradle
    gradle=$(find_gradle)
    if [ -z "$gradle" ]; then
        echo "No gradle 8.2 found; cannot build $jar. Build it manually or restore the jar." >&2
        return 1
    fi
    echo "Building $module ..."
    (cd "$module" && "$gradle" bootJar --offline -q)
}

wait_for_port() {
    local host="$1" port="$2" label="$3" tries=90
    while [ "$tries" -gt 0 ]; do
        if (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null; then
            return 0
        fi
        tries=$((tries - 1))
        sleep 1
    done
    echo "Timed out waiting for $label on $host:$port" >&2
    return 1
}

# The address DHCP gave us. `hostname -I` is not available everywhere, so ask
# the routing table first and fall back to enumerating global interfaces.
detect_lan_ip() {
    local ip=""
    ip=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p' | head -1)
    if [ -z "$ip" ]; then
        ip=$(hostname -i 2>/dev/null | awk '{print $1}')
    fi
    case "$ip" in
        ""|127.*) ip=$(ip -4 -o addr show scope global 2>/dev/null | awk '{split($4,a,"/"); print a[1]; exit}') ;;
    esac
    printf '%s' "${ip:-127.0.0.1}"
}

cleanup() {
    trap - INT TERM EXIT
    [ -n "${GATEWAY_PID:-}" ] && kill "$GATEWAY_PID" 2>/dev/null || true
    [ -n "${RMI_PID:-}" ] && kill "$RMI_PID" 2>/dev/null || true
    wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

build_jar rmi-server "$RMI_JAR"
build_jar api-gateway "$GATEWAY_JAR"

echo "Starting RMI core server (HTTP 8081, RMI 1099) ..."
java -jar "$RMI_JAR" >rmi-server.log 2>&1 &
RMI_PID=$!
wait_for_port 127.0.0.1 1099 "RMI registry"

echo "Starting API gateway (HTTP 8080) ..."
java -jar "$GATEWAY_JAR" >api-gateway.log 2>&1 &
GATEWAY_PID=$!
wait_for_port 127.0.0.1 8080 "API gateway"

LAN_IP=$(detect_lan_ip)
cat <<EOF

──────────────────────────────────────────────────────────────
  Banking stack is up.

  API gateway : http://${LAN_IP:-<lan-ip>}:8080/api/v1
  Health      : http://${LAN_IP:-<lan-ip>}:8080/api/v1/health
  RMI registry: 127.0.0.1:1099   (core server HTTP on 8081)
  Discovery   : UDP $DISCOVERY_PORT

  The Android app finds the gateway automatically, so a new DHCP
  address needs no rebuild. If the phone cannot reach the gateway,
  allow the ports through the firewall:
      sudo ufw allow 8080/tcp
      sudo ufw allow $DISCOVERY_PORT/udp

  Logs: rmi-server.log, api-gateway.log   (Ctrl+C to stop)
──────────────────────────────────────────────────────────────
EOF

wait
