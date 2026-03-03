#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# start-services.sh — Build (optional) and start all HMS services
#
# Usage:
#   ./scripts/start-services.sh           # start with existing JARs
#   ./scripts/start-services.sh --build   # mvn package first, then start
#   ./scripts/start-services.sh --stop    # stop all running services
#
# Prerequisites:
#   1. docker compose up -d               (Postgres, NATS, Redis, Keycloak, MinIO)
#   2. ./scripts/setup-keycloak.sh        (first run only)
#   3. This script
#
# Logs: each service writes to logs/<name>.log
# PIDs: stored in logs/<name>.pid  (used by --stop)
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*"; }
header()  { echo -e "\n${BOLD}$*${RESET}"; }

# ── Service definitions ───────────────────────────────────────────────────────
# Format: "name|directory|port|jar-glob"
declare -a JAVA_SERVICES=(
  "reception-service|reception-service|3001|reception-*.jar"
  "clinical-service|clinical-service|3002|clinical-service-*.jar"
  "staff-service|staff-service|3003|staff-*.jar"
  "finance-service|finance-service|3004|finance-*.jar"
  "lab-service|lab-service|3005|lab-*.jar"
  "ward-service|ward-service|3006|ward-*.jar"
  "gateway|gateway|3000|hms-gateway-*.jar"
)

NODE_SERVICE="ws-bridge|ws-bridge|3010"

# ── --stop flag ───────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--stop" ]]; then
  header "Stopping all HMS services..."
  stopped=0
  for svc_def in "${JAVA_SERVICES[@]}"; do
    IFS='|' read -r name _ port _ <<< "$svc_def"
    pid_file="$LOG_DIR/$name.pid"
    if [[ -f "$pid_file" ]]; then
      pid=$(cat "$pid_file")
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" && success "Stopped $name (PID $pid)"
        stopped=$((stopped + 1))
      else
        warn "$name PID $pid not running"
      fi
      rm -f "$pid_file"
    else
      # fallback: kill by port
      pid=$(lsof -ti tcp:"$port" 2>/dev/null || true)
      if [[ -n "$pid" ]]; then
        kill "$pid" && success "Stopped $name (port $port, PID $pid)"
        stopped=$((stopped + 1))
      else
        warn "$name not running"
      fi
    fi
  done

  # ws-bridge
  pid_file="$LOG_DIR/ws-bridge.pid"
  if [[ -f "$pid_file" ]]; then
    pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" && success "Stopped ws-bridge (PID $pid)"
      stopped=$((stopped + 1))
    fi
    rm -f "$pid_file"
  fi

  echo ""
  info "Stopped $stopped service(s)."
  exit 0
fi

# ── --build flag ──────────────────────────────────────────────────────────────
BUILD=false
if [[ "${1:-}" == "--build" ]]; then
  BUILD=true
fi

# ── Build ─────────────────────────────────────────────────────────────────────
if $BUILD; then
  header "Building all Java services..."
  for svc_def in "${JAVA_SERVICES[@]}"; do
    IFS='|' read -r name dir port _ <<< "$svc_def"
    svc_path="$ROOT_DIR/$dir"
    info "Building $name..."
    if mvn -f "$svc_path/pom.xml" package -DskipTests -q; then
      success "$name built"
    else
      error "$name build FAILED — aborting"
      exit 1
    fi
  done
fi

# ── Helper: find JAR ──────────────────────────────────────────────────────────
find_jar() {
  local dir="$1" glob="$2"
  # shellcheck disable=SC2086
  local jar
  jar=$(ls "$dir/target/"$glob 2>/dev/null | grep -v '\.original$' | head -1)
  echo "$jar"
}

# ── Helper: wait for port ─────────────────────────────────────────────────────
wait_for_port() {
  local name="$1" port="$2" timeout="${3:-60}"
  local elapsed=0
  while ! nc -z localhost "$port" 2>/dev/null; do
    if (( elapsed >= timeout )); then
      error "$name did not come up on port $port within ${timeout}s"
      error "Check logs/$name.log for details"
      return 1
    fi
    sleep 2
    (( elapsed += 2 ))
  done
  return 0
}

# ── Start Java services ───────────────────────────────────────────────────────
header "Starting HMS Java services..."
echo ""

for svc_def in "${JAVA_SERVICES[@]}"; do
  IFS='|' read -r name dir port jar_glob <<< "$svc_def"
  svc_path="$ROOT_DIR/$dir"
  log_file="$LOG_DIR/$name.log"
  pid_file="$LOG_DIR/$name.pid"

  # Check if already running
  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    warn "$name is already running (PID $(cat "$pid_file")) — skipping"
    continue
  fi

  # Find JAR
  jar=$(find_jar "$svc_path" "$jar_glob")
  if [[ -z "$jar" ]]; then
    error "No JAR found for $name in $svc_path/target/ (glob: $jar_glob)"
    error "Run with --build to build first, or run: mvn -f $svc_path/pom.xml package -DskipTests"
    exit 1
  fi

  info "Starting $name on port $port..."
  info "  JAR: $(basename "$jar")"
  info "  Log: logs/$name.log"

  # Launch in background
  java -jar "$jar" \
    >> "$log_file" 2>&1 &
  echo $! > "$pid_file"

  # Wait for the port to open
  if wait_for_port "$name" "$port" 60; then
    success "$name is UP  (PID $(cat "$pid_file"), port $port)"
  else
    exit 1
  fi

  echo ""
done

# ── Start ws-bridge (Node.js) ─────────────────────────────────────────────────
header "Starting ws-bridge (Node.js)..."
WS_DIR="$ROOT_DIR/ws-bridge"
WS_LOG="$LOG_DIR/ws-bridge.log"
WS_PID="$LOG_DIR/ws-bridge.pid"
WS_PORT=3010

if [[ -f "$WS_PID" ]] && kill -0 "$(cat "$WS_PID")" 2>/dev/null; then
  warn "ws-bridge is already running (PID $(cat "$WS_PID")) — skipping"
else
  if [[ ! -d "$WS_DIR/node_modules" ]]; then
    info "Installing ws-bridge dependencies..."
    npm install --prefix "$WS_DIR" --silent
  fi

  info "Starting ws-bridge on port $WS_PORT..."
  info "  Log: logs/ws-bridge.log"
  node "$WS_DIR/src/index.js" >> "$WS_LOG" 2>&1 &
  echo $! > "$WS_PID"

  if wait_for_port "ws-bridge" "$WS_PORT" 15; then
    success "ws-bridge is UP  (PID $(cat "$WS_PID"), port $WS_PORT)"
  else
    exit 1
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}════════════════════════════════════════${RESET}"
echo -e "${GREEN}${BOLD}  All services started successfully!${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
echo ""
echo "  Service             Port   PID"
echo "  ──────────────────  ─────  ──────"
for svc_def in "${JAVA_SERVICES[@]}"; do
  IFS='|' read -r name _ port _ <<< "$svc_def"
  pid=$(cat "$LOG_DIR/$name.pid" 2>/dev/null || echo "?")
  printf "  %-20s %-6s %s\n" "$name" "$port" "$pid"
done
printf "  %-20s %-6s %s\n" "ws-bridge" "$WS_PORT" "$(cat "$LOG_DIR/ws-bridge.pid" 2>/dev/null || echo '?')"
echo ""
echo "  Logs: $LOG_DIR/"
echo ""
echo "  To stop all:  ./scripts/start-services.sh --stop"
echo ""
