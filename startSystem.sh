#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ML_DIR="$ROOT_DIR/demand-forecasting/ml"
BACKEND_DIR="$ROOT_DIR/demand-forecasting/backend"
LOG_DIR="$ROOT_DIR/logs"
ML_LOG="$LOG_DIR/ml.log"
BACKEND_LOG="$LOG_DIR/backend.log"
SYSTEM_LOG="$LOG_DIR/system-runtime.log"

ML_PORT="${ML_PORT:-5001}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
BACKEND_PROFILE="${BACKEND_PROFILE:-test}"
STARTUP_TIMEOUT_SECS="${STARTUP_TIMEOUT_SECS:-180}"
RESET_LOGS_ON_EXIT="${RESET_LOGS_ON_EXIT:-true}"

ML_PID=""
BACKEND_PID=""
ML_TAIL_PID=""
BACKEND_TAIL_PID=""
STOPPING="0"

mkdir -p "$LOG_DIR"

timestamp() {
  date '+%Y-%m-%dT%H:%M:%S%z'
}

append_system_log() {
  local level="$1"
  local message="$2"
  printf '[%s] [%s] %s\n' "$(timestamp)" "$level" "$message" >> "$SYSTEM_LOG"
}

info() {
  append_system_log "INFO" "$1"
  printf '[INFO] %s\n' "$1"
}

warn() {
  append_system_log "WARN" "$1"
  printf '[WARN] %s\n' "$1"
}

error() {
  append_system_log "ERROR" "$1"
  printf '[ERROR] %s\n' "$1" >&2
}

classify_error_from_log() {
  local file="$1"
  if [ ! -f "$file" ]; then
    printf 'UNKNOWN_ERROR'
    return
  fi

  if grep -qi 'address already in use\|port .* already in use\|failed to bind' "$file"; then
    printf 'PORT_IN_USE'
  elif grep -qi 'No such file or directory\|command not found\|cannot open' "$file"; then
    printf 'COMMAND_OR_PATH_ERROR'
  elif grep -qi 'ModuleNotFoundError\|ImportError\|ClassNotFoundException\|NoClassDefFoundError' "$file"; then
    printf 'DEPENDENCY_ERROR'
  elif grep -qi 'permission denied\|operation not permitted' "$file"; then
    printf 'PERMISSION_ERROR'
  elif grep -qi 'exception\|traceback\|error' "$file"; then
    printf 'RUNTIME_EXCEPTION'
  else
    printf 'UNKNOWN_ERROR'
  fi
}

print_failure_details() {
  local service="$1"
  local reason="$2"
  local logfile="$3"
  local error_type

  error_type="$(classify_error_from_log "$logfile")"
  error "${service} failed to start"
  error "Failure reason: ${reason}"
  error "Error type: ${error_type}"

  if [ -f "$logfile" ]; then
    error "Last 40 log lines from ${service}:"
    {
      printf -- '----- Last 40 lines from %s -----\n' "$service"
      tail -n 40 "$logfile"
      printf -- '----- End of %s log excerpt -----\n' "$service"
    } >> "$SYSTEM_LOG"
    tail -n 40 "$logfile" >&2
  else
    error "No log file found for ${service} (${logfile})"
  fi
}

cleanup() {
  if [ "$STOPPING" = "1" ]; then
    return
  fi
  STOPPING="1"

  info 'Stopping services...'

  if [ -n "$ML_PID" ] && kill -0 "$ML_PID" 2>/dev/null; then
    kill "$ML_PID" 2>/dev/null || true
  fi

  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi

  sleep 1

  if [ -n "$ML_PID" ] && kill -0 "$ML_PID" 2>/dev/null; then
    warn 'ML service did not stop gracefully; sending SIGKILL.'
    kill -9 "$ML_PID" 2>/dev/null || true
  fi

  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    warn 'Backend service did not stop gracefully; sending SIGKILL.'
    kill -9 "$BACKEND_PID" 2>/dev/null || true
  fi

  # Safety net: kill any leftover listeners on the configured service ports.
  kill_port_listener "$ML_PORT" 'ML service'
  kill_port_listener "$BACKEND_PORT" 'Backend service'
  stop_log_streams

  info 'All services stopped.'

  if [ "$RESET_LOGS_ON_EXIT" = "true" ]; then
    reset_runtime_logs
    printf '[INFO] Runtime logs reset.\n'
  fi
}

on_interrupt() {
  info 'Ctrl+C received.'
  cleanup
  exit 0
}

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    error "Missing required command: ${cmd}"
    exit 1
  fi
}

initialize_logs() {
  : > "$SYSTEM_LOG"
  : > "$ML_LOG"
  : > "$BACKEND_LOG"
}

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  if command -v nc >/dev/null 2>&1; then
    nc -z 127.0.0.1 "$port" >/dev/null 2>&1
    return $?
  fi

  return 1
}

kill_port_listener() {
  local port="$1"
  local name="$2"

  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi

  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | tr '\n' ' ')"
  if [ -z "$pids" ]; then
    return 0
  fi

  warn "${name} still listening on port ${port}; terminating listener process(es): ${pids}"
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  sleep 1

  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    local force_pids
    force_pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | tr '\n' ' ')"
    warn "${name} listener still alive on port ${port}; sending SIGKILL: ${force_pids}"
    # shellcheck disable=SC2086
    kill -9 $force_pids 2>/dev/null || true
  fi
}

start_log_streams() {
  (
    tail -n0 -F "$ML_LOG" 2>/dev/null | while IFS= read -r line; do
      append_system_log "ML" "$line"
    done
  ) &
  ML_TAIL_PID="$!"

  (
    tail -n0 -F "$BACKEND_LOG" 2>/dev/null | while IFS= read -r line; do
      append_system_log "BACKEND" "$line"
    done
  ) &
  BACKEND_TAIL_PID="$!"
}

stop_log_streams() {
  if [ -n "$ML_TAIL_PID" ] && kill -0 "$ML_TAIL_PID" 2>/dev/null; then
    kill "$ML_TAIL_PID" 2>/dev/null || true
  fi
  if [ -n "$BACKEND_TAIL_PID" ] && kill -0 "$BACKEND_TAIL_PID" 2>/dev/null; then
    kill "$BACKEND_TAIL_PID" 2>/dev/null || true
  fi
}

reset_runtime_logs() {
  : > "$ML_LOG"
  : > "$BACKEND_LOG"
  : > "$SYSTEM_LOG"
}

resolve_java_home() {
  if [ -n "${JAVA_HOME:-}" ]; then
    printf '%s' "$JAVA_HOME"
    return
  fi

  if [ "$(uname -s)" = 'Darwin' ] && [ -x '/usr/libexec/java_home' ]; then
    /usr/libexec/java_home -v 23 2>/dev/null && return
    /usr/libexec/java_home -v 21 2>/dev/null && return
    /usr/libexec/java_home -v 17 2>/dev/null && return
  fi

  printf ''
}

wait_for_health_or_fail() {
  local name="$1"
  local pid="$2"
  local url="$3"
  local log_file="$4"

  local start_ts now
  start_ts="$(date +%s)"

  while true; do
    if ! kill -0 "$pid" 2>/dev/null; then
      print_failure_details "$name" 'process exited during startup' "$log_file"
      return 1
    fi

    if curl -fsS "$url" >/dev/null 2>&1; then
      info "${name} is healthy."
      return 0
    fi

    now="$(date +%s)"
    if [ $((now - start_ts)) -ge "$STARTUP_TIMEOUT_SECS" ]; then
      print_failure_details "$name" "health check timeout after ${STARTUP_TIMEOUT_SECS}s" "$log_file"
      return 1
    fi

    sleep 1
  done
}

start_ml() {
  if [ ! -d "$ML_DIR" ]; then
    error "ML directory not found: $ML_DIR"
    return 1
  fi

  local ml_python="$ML_DIR/.venv/bin/python"
  if [ ! -x "$ml_python" ]; then
    error "ML virtualenv python not found or not executable: $ml_python"
    return 1
  fi

  : > "$ML_LOG"

  (
    cd "$ML_DIR" || exit 1
    exec env MODEL_DIR="$ML_DIR/models" "$ml_python" -m flask --app app:create_app run --host 127.0.0.1 --port "$ML_PORT"
  ) > "$ML_LOG" 2>&1 &
  ML_PID="$!"

  info "Started ML service (PID $ML_PID), logs: $ML_LOG"
  return 0
}

start_backend() {
  if [ ! -d "$BACKEND_DIR" ]; then
    error "Backend directory not found: $BACKEND_DIR"
    return 1
  fi

  : > "$BACKEND_LOG"

  local java_home
  java_home="$(resolve_java_home)"

  (
    cd "$BACKEND_DIR" || exit 1
    if [ -n "$java_home" ]; then
      exec env JAVA_HOME="$java_home" mvn -DskipTests \
        -Dspring-boot.run.useTestClasspath=true \
        -Dspring-boot.run.profiles="$BACKEND_PROFILE" \
        -Dspring-boot.run.arguments="--ml.api.base-url=http://127.0.0.1:${ML_PORT}" \
        spring-boot:run
    else
      exec mvn -DskipTests \
        -Dspring-boot.run.useTestClasspath=true \
        -Dspring-boot.run.profiles="$BACKEND_PROFILE" \
        -Dspring-boot.run.arguments="--ml.api.base-url=http://127.0.0.1:${ML_PORT}" \
        spring-boot:run
    fi
  ) > "$BACKEND_LOG" 2>&1 &
  BACKEND_PID="$!"

  info "Started backend service (PID $BACKEND_PID), logs: $BACKEND_LOG"
  return 0
}

monitor_runtime() {
  while true; do
    if [ -n "$ML_PID" ] && ! kill -0 "$ML_PID" 2>/dev/null; then
      print_failure_details 'ML service' 'process exited unexpectedly' "$ML_LOG"
      cleanup
      exit 1
    fi

    if [ -n "$BACKEND_PID" ] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      print_failure_details 'Backend service' 'process exited unexpectedly' "$BACKEND_LOG"
      cleanup
      exit 1
    fi

    sleep 2
  done
}

main() {
  require_command curl
  require_command mvn
  initialize_logs

  if port_in_use "$ML_PORT"; then
    error "Port $ML_PORT is already in use (ML service port)."
    error 'Error type: PORT_IN_USE'
    exit 1
  fi

  if port_in_use "$BACKEND_PORT"; then
    error "Port $BACKEND_PORT is already in use (backend port)."
    error 'Error type: PORT_IN_USE'
    exit 1
  fi

  trap on_interrupt INT TERM
  trap cleanup EXIT

  info "Starting ML service on http://127.0.0.1:${ML_PORT}"
  start_ml || exit 1

  info "Starting backend service on http://127.0.0.1:${BACKEND_PORT}"
  start_backend || exit 1
  start_log_streams

  wait_for_health_or_fail 'ML service' "$ML_PID" "http://127.0.0.1:${ML_PORT}/health" "$ML_LOG" || exit 1
  wait_for_health_or_fail 'Backend service' "$BACKEND_PID" "http://127.0.0.1:${BACKEND_PORT}/actuator/health" "$BACKEND_LOG" || exit 1

  echo
  info 'Application running.'
  info "Backend: http://127.0.0.1:${BACKEND_PORT}"
  info "Swagger UI: http://127.0.0.1:${BACKEND_PORT}/swagger-ui/index.html"
  info "ML API: http://127.0.0.1:${ML_PORT}"
  info 'Press Ctrl+C to stop both services.'
  echo

  monitor_runtime
}

main "$@"
