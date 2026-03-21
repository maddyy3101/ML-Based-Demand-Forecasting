#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ML_DIR="$ROOT_DIR/demand-forecasting/ml"
BACKEND_DIR="$ROOT_DIR/demand-forecasting/backend"
FRONTEND_DIR="$ROOT_DIR/demand-forecasting/frontend"
LOG_DIR="$ROOT_DIR/logs"
ML_LOG="$LOG_DIR/ml.log"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
SYSTEM_LOG="$LOG_DIR/system-runtime.log"

ML_PORT="${ML_PORT:-5001}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_PROFILE="${BACKEND_PROFILE:-auto}"
STARTUP_TIMEOUT_SECS="${STARTUP_TIMEOUT_SECS:-180}"
RESET_LOGS_ON_EXIT="${RESET_LOGS_ON_EXIT:-true}"
AUTO_START_POSTGRES="${AUTO_START_POSTGRES:-true}"
RUN_DB_BOOTSTRAP_SQL="${RUN_DB_BOOTSTRAP_SQL:-true}"
ENV_FILE="${ENV_FILE:-}"

POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-powergrid}"
POSTGRES_USER="${POSTGRES_USER:-}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"
APP_DB_USER="${APP_DB_USER:-}"
APP_DB_PASSWORD="${APP_DB_PASSWORD:-}"
DB_BOOTSTRAP_SQL_FILE="${DB_BOOTSTRAP_SQL_FILE:-$ROOT_DIR/demand-forecasting/backend/sql/bootstrap_postgres.sql}"

ML_PID=""
BACKEND_PID=""
FRONTEND_PID=""
ML_TAIL_PID=""
BACKEND_TAIL_PID=""
FRONTEND_TAIL_PID=""
STOPPING="0"
RESOLVED_BACKEND_PROFILE=""

mkdir -p "$LOG_DIR"

timestamp() {
  date '+%Y-%m-%dT%H:%M:%S%z'
}

ensure_postgres_cli_path() {
  local pg_bin=""
  if [ -d "/opt/homebrew/opt/postgresql@16/bin" ]; then
    pg_bin="/opt/homebrew/opt/postgresql@16/bin"
  elif [ -d "/usr/local/opt/postgresql@16/bin" ]; then
    pg_bin="/usr/local/opt/postgresql@16/bin"
  fi

  if [ -n "$pg_bin" ] && [[ ":$PATH:" != *":$pg_bin:"* ]]; then
    export PATH="$pg_bin:$PATH"
  fi
}

load_local_env() {
  local env_path="${ENV_FILE:-}"

  if [ -z "$env_path" ]; then
    if [ -f "$ROOT_DIR/.env.local" ]; then
      env_path="$ROOT_DIR/.env.local"
    elif [ -f "$ROOT_DIR/.env" ]; then
      env_path="$ROOT_DIR/.env"
    fi
  fi

  if [ -n "$env_path" ]; then
    if [ ! -f "$env_path" ]; then
      warn "ENV_FILE is set but file does not exist: $env_path"
    else
      # Export everything from env file for child processes (backend/ml/frontend).
      set -a
      # shellcheck disable=SC1090
      . "$env_path"
      set +a
      info "Loaded environment config from: $env_path"
    fi
  else
    warn "No env file found (.env.local or .env)."
  fi

  if [ -n "${GROQ_API_KEY:-}" ]; then
    info "ProcBot API key loaded from environment (Groq)."
  elif [ -n "${GEMINI_API_KEY:-}" ]; then
    info "ProcBot API key loaded from environment (Gemini)."
  else
    warn "ProcBot API key not set. Configure GROQ_API_KEY or GEMINI_API_KEY in env."
  fi
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

  if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null || true
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

  if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    warn 'Frontend service did not stop gracefully; sending SIGKILL.'
    kill -9 "$FRONTEND_PID" 2>/dev/null || true
  fi

  # Safety net: kill any leftover listeners on the configured service ports.
  kill_port_listener "$ML_PORT" 'ML service'
  kill_port_listener "$BACKEND_PORT" 'Backend service'
  kill_port_listener "$FRONTEND_PORT" 'Frontend service'
  stop_log_streams

  info 'All services stopped.'

  if [ "$RESET_LOGS_ON_EXIT" = "true" ]; then
    reset_runtime_logs
    printf '[INFO] Runtime logs reset.\n'
  fi
}

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    error "Missing required command: ${cmd}"
    exit 1
  fi
}

on_interrupt() {
  info 'Ctrl+C received.'
  cleanup
  exit 0
}

initialize_logs() {
  : > "$SYSTEM_LOG"
  : > "$ML_LOG"
  : > "$BACKEND_LOG"
  : > "$FRONTEND_LOG"
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

listener_pid_for_port() {
  local port="$1"
  if ! command -v lsof >/dev/null 2>&1; then
    printf ''
    return
  fi
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1
}

can_connect_postgres() {
  if command -v nc >/dev/null 2>&1; then
    if nc -z "$POSTGRES_HOST" "$POSTGRES_PORT" >/dev/null 2>&1; then
      return 0
    fi
  fi

  if command -v lsof >/dev/null 2>&1; then
    if lsof -nP -iTCP:"$POSTGRES_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      return 0
    fi
  fi

  return 1
}

run_psql_command() {
  local db="$1"
  local sql="$2"
  local compose_file="$ROOT_DIR/demand-forecasting/docker-compose.yml"

  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$POSTGRES_PASSWORD" psql \
      -h "$POSTGRES_HOST" \
      -p "$POSTGRES_PORT" \
      -U "$POSTGRES_USER" \
      -d "$db" \
      -v ON_ERROR_STOP=1 \
      -qtAX \
      -c "$sql"
    return $?
  fi

  if command -v docker >/dev/null 2>&1 && [ -f "$compose_file" ]; then
    docker compose -f "$compose_file" exec -T powergrid-postgres \
      psql -U "$POSTGRES_USER" -d "$db" -v ON_ERROR_STOP=1 -qtAX -c "$sql"
    return $?
  fi

  return 1
}

run_psql_file() {
  local db="$1"
  local sql_file="$2"
  local compose_file="$ROOT_DIR/demand-forecasting/docker-compose.yml"

  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$POSTGRES_PASSWORD" psql \
      -h "$POSTGRES_HOST" \
      -p "$POSTGRES_PORT" \
      -U "$POSTGRES_USER" \
      -d "$db" \
      -v ON_ERROR_STOP=1 \
      -f "$sql_file"
    return $?
  fi

  if command -v docker >/dev/null 2>&1 && [ -f "$compose_file" ]; then
    docker compose -f "$compose_file" exec -T powergrid-postgres \
      psql -U "$POSTGRES_USER" -d "$db" -v ON_ERROR_STOP=1 -f - < "$sql_file"
    return $?
  fi

  return 1
}

can_execute_sql_bootstrap() {
  if command -v psql >/dev/null 2>&1; then
    return 0
  fi

  if command -v docker >/dev/null 2>&1 && [ -f "$ROOT_DIR/demand-forecasting/docker-compose.yml" ]; then
    return 0
  fi

  return 1
}

ensure_postgres_database() {
  local exists
  exists="$(run_psql_command "postgres" "SELECT 1 FROM pg_database WHERE datname='${POSTGRES_DB}'" 2>/dev/null | tr -d '[:space:]' || true)"
  if [ "$exists" != "1" ]; then
    info "Creating PostgreSQL database '${POSTGRES_DB}'..."
    run_psql_command "postgres" "CREATE DATABASE \"${POSTGRES_DB}\";" >/dev/null || return 1
  fi
  return 0
}

bootstrap_postgres_sql() {
  if [ "$RESOLVED_BACKEND_PROFILE" != "local" ]; then
    return 0
  fi

  if ! can_execute_sql_bootstrap; then
    warn "Skipping SQL bootstrap: neither 'psql' nor Docker Compose is available."
    return 0
  fi

  if ! ensure_postgres_database; then
    error "Failed to verify/create PostgreSQL database '${POSTGRES_DB}'."
    return 1
  fi

  if [ "$RUN_DB_BOOTSTRAP_SQL" != "true" ]; then
    return 0
  fi

  if [ ! -f "$DB_BOOTSTRAP_SQL_FILE" ]; then
    warn "SQL bootstrap file not found: $DB_BOOTSTRAP_SQL_FILE (skipping)."
    return 0
  fi

  info "Applying SQL bootstrap script: $DB_BOOTSTRAP_SQL_FILE"
  if ! run_psql_file "$POSTGRES_DB" "$DB_BOOTSTRAP_SQL_FILE" >> "$SYSTEM_LOG" 2>&1; then
    error "SQL bootstrap failed while applying: $DB_BOOTSTRAP_SQL_FILE"
    return 1
  fi

  info "SQL bootstrap applied successfully."
  return 0
}

resolve_backend_profile() {
  local local_default_user
  local_default_user="$(id -un 2>/dev/null || printf 'postgres')"

  case "$BACKEND_PROFILE" in
    auto)
      if can_connect_postgres; then
        RESOLVED_BACKEND_PROFILE="local"
        info 'Detected PostgreSQL on localhost:5432. Using backend profile: local (persistent logins).'
      else
        if [ "$AUTO_START_POSTGRES" = "true" ] && start_postgres_if_possible; then
          RESOLVED_BACKEND_PROFILE="local"
          info 'PostgreSQL started successfully. Using backend profile: local (persistent logins).'
        else
          RESOLVED_BACKEND_PROFILE="test"
          warn 'PostgreSQL not detected on localhost:5432. Falling back to profile: test (in-memory DB).'
        fi
      fi
      ;;
    local|test|docker)
      RESOLVED_BACKEND_PROFILE="$BACKEND_PROFILE"
      ;;
    *)
      error "Unsupported BACKEND_PROFILE: $BACKEND_PROFILE (allowed: auto, local, test, docker)"
      return 1
      ;;
  esac

  if [ "$RESOLVED_BACKEND_PROFILE" = "local" ] && ! can_connect_postgres; then
    if [ "$AUTO_START_POSTGRES" = "true" ] && start_postgres_if_possible; then
      return 0
    fi
    error 'Selected profile local but PostgreSQL is unreachable on localhost:5432.'
    error 'Start PostgreSQL first (or run with BACKEND_PROFILE=test for temporary in-memory DB).'
    return 1
  fi

  if [ "$RESOLVED_BACKEND_PROFILE" = "local" ]; then
    if [ -z "$POSTGRES_USER" ]; then
      POSTGRES_USER="$local_default_user"
    fi
    if [ -z "$APP_DB_USER" ]; then
      APP_DB_USER="$local_default_user"
    fi
    # Homebrew local postgres usually uses peer/trust auth by default (empty password).
    if [ -z "$APP_DB_PASSWORD" ]; then
      APP_DB_PASSWORD=""
    fi
  else
    if [ -z "$POSTGRES_USER" ]; then
      POSTGRES_USER="postgres"
    fi
  fi
}

start_postgres_if_possible() {
  local compose_file="$ROOT_DIR/demand-forecasting/docker-compose.yml"

  if ! command -v docker >/dev/null 2>&1; then
    warn 'Docker not found. Cannot auto-start PostgreSQL.'
    return 1
  fi

  if [ ! -f "$compose_file" ]; then
    warn "Docker compose file not found: $compose_file"
    return 1
  fi

  info 'PostgreSQL not running. Attempting to start powergrid-postgres via Docker Compose...'
  if ! docker compose -f "$compose_file" up -d powergrid-postgres >/dev/null 2>&1; then
    warn 'Failed to start powergrid-postgres with Docker Compose.'
    return 1
  fi

  local attempts=30
  while [ $attempts -gt 0 ]; do
    if can_connect_postgres; then
      return 0
    fi
    sleep 1
    attempts=$((attempts - 1))
  done

  warn 'powergrid-postgres did not become reachable on localhost:5432 in time.'
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

  (
    tail -n0 -F "$FRONTEND_LOG" 2>/dev/null | while IFS= read -r line; do
      append_system_log "FRONTEND" "$line"
    done
  ) &
  FRONTEND_TAIL_PID="$!"
}

stop_log_streams() {
  if [ -n "$ML_TAIL_PID" ] && kill -0 "$ML_TAIL_PID" 2>/dev/null; then
    kill "$ML_TAIL_PID" 2>/dev/null || true
  fi
  if [ -n "$BACKEND_TAIL_PID" ] && kill -0 "$BACKEND_TAIL_PID" 2>/dev/null; then
    kill "$BACKEND_TAIL_PID" 2>/dev/null || true
  fi
  if [ -n "$FRONTEND_TAIL_PID" ] && kill -0 "$FRONTEND_TAIL_PID" 2>/dev/null; then
    kill "$FRONTEND_TAIL_PID" 2>/dev/null || true
  fi
}

reset_runtime_logs() {
  : > "$ML_LOG"
  : > "$BACKEND_LOG"
  : > "$FRONTEND_LOG"
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
  local pid_var="$2"
  local url="$3"
  local log_file="$4"
  local port="$5"

  local start_ts now pid listener_pid
  start_ts="$(date +%s)"
  pid="${!pid_var:-}"

  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      listener_pid="$(listener_pid_for_port "$port")"
      if [ -n "$listener_pid" ]; then
        printf -v "$pid_var" '%s' "$listener_pid"
      fi
      info "${name} is healthy."
      return 0
    fi

    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      listener_pid="$(listener_pid_for_port "$port")"
      if [ -n "$listener_pid" ]; then
        printf -v "$pid_var" '%s' "$listener_pid"
        pid="$listener_pid"
      else
        print_failure_details "$name" 'process exited during startup' "$log_file"
        return 1
      fi
    fi

    now="$(date +%s)"
    if [ $((now - start_ts)) -ge "$STARTUP_TIMEOUT_SECS" ]; then
      print_failure_details "$name" "health check timeout after ${STARTUP_TIMEOUT_SECS}s" "$log_file"
      return 1
    fi

    sleep 1
    pid="${!pid_var:-}"
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
  local run_args
  java_home="$(resolve_java_home)"
  run_args="--ml.api.base-url=http://127.0.0.1:${ML_PORT}"

  if [ "$RESOLVED_BACKEND_PROFILE" = "local" ]; then
    local db_user
    local db_password
    db_user="$APP_DB_USER"
    db_password="$APP_DB_PASSWORD"
    run_args="${run_args} --spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB} --spring.datasource.username=${db_user} --spring.datasource.password=${db_password}"
  fi

  (
    cd "$BACKEND_DIR" || exit 1
    if [ -n "$java_home" ]; then
      exec env JAVA_HOME="$java_home" mvn -DskipTests \
        -Dspring-boot.run.useTestClasspath=true \
        -Dspring-boot.run.profiles="$RESOLVED_BACKEND_PROFILE" \
        -Dspring-boot.run.arguments="$run_args" \
        spring-boot:run
    else
      exec mvn -DskipTests \
        -Dspring-boot.run.useTestClasspath=true \
        -Dspring-boot.run.profiles="$RESOLVED_BACKEND_PROFILE" \
        -Dspring-boot.run.arguments="$run_args" \
        spring-boot:run
    fi
  ) > "$BACKEND_LOG" 2>&1 &
  BACKEND_PID="$!"

  info "Started backend service (PID $BACKEND_PID), logs: $BACKEND_LOG"
  return 0
}

start_frontend() {
  if [ ! -d "$FRONTEND_DIR" ]; then
    error "Frontend directory not found: $FRONTEND_DIR"
    return 1
  fi

  if [ ! -f "$FRONTEND_DIR/package.json" ]; then
    error "package.json not found in $FRONTEND_DIR"
    return 1
  fi

  : > "$FRONTEND_LOG"

  (
    cd "$FRONTEND_DIR" || exit 1
    if [ ! -d "node_modules" ]; then
      npm install >> "$FRONTEND_LOG" 2>&1
    fi
    exec npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort
  ) > "$FRONTEND_LOG" 2>&1 &
  FRONTEND_PID="$!"

  info "Started frontend service (PID $FRONTEND_PID), logs: $FRONTEND_LOG"
  return 0
}

monitor_runtime() {
  local listener_pid
  while true; do
    if [ -n "$ML_PID" ] && ! kill -0 "$ML_PID" 2>/dev/null; then
      listener_pid="$(listener_pid_for_port "$ML_PORT")"
      if [ -n "$listener_pid" ]; then
        ML_PID="$listener_pid"
      else
        print_failure_details 'ML service' 'process exited unexpectedly' "$ML_LOG"
        cleanup
        exit 1
      fi
    fi

    if [ -n "$BACKEND_PID" ] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      listener_pid="$(listener_pid_for_port "$BACKEND_PORT")"
      if [ -n "$listener_pid" ]; then
        BACKEND_PID="$listener_pid"
      else
        print_failure_details 'Backend service' 'process exited unexpectedly' "$BACKEND_LOG"
        cleanup
        exit 1
      fi
    fi

    if [ -n "$FRONTEND_PID" ] && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
      listener_pid="$(listener_pid_for_port "$FRONTEND_PORT")"
      if [ -n "$listener_pid" ]; then
        FRONTEND_PID="$listener_pid"
      else
        print_failure_details 'Frontend service' 'process exited unexpectedly' "$FRONTEND_LOG"
        cleanup
        exit 1
      fi
    fi

    sleep 2
  done
}

main() {
  ensure_postgres_cli_path
  require_command curl
  require_command mvn
  require_command npm
  initialize_logs
  load_local_env
  resolve_backend_profile || exit 1
  bootstrap_postgres_sql || exit 1

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

  if port_in_use "$FRONTEND_PORT"; then
    error "Port $FRONTEND_PORT is already in use (frontend port)."
    error 'Error type: PORT_IN_USE'
    exit 1
  fi

  trap on_interrupt INT TERM
  trap cleanup EXIT

  info "Starting ML service on http://127.0.0.1:${ML_PORT}"
  start_ml || exit 1

  info "Starting backend service on http://127.0.0.1:${BACKEND_PORT}"
  info "Backend profile: ${RESOLVED_BACKEND_PROFILE}"
  if [ "$RESOLVED_BACKEND_PROFILE" = "local" ]; then
    info "Backend DB: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB} (user=${APP_DB_USER})"
  fi
  start_backend || exit 1

  info "Starting frontend service on http://127.0.0.1:${FRONTEND_PORT}"
  start_frontend || exit 1
  start_log_streams

  wait_for_health_or_fail 'ML service' ML_PID "http://127.0.0.1:${ML_PORT}/health" "$ML_LOG" "$ML_PORT" || exit 1
  wait_for_health_or_fail 'Backend service' BACKEND_PID "http://127.0.0.1:${BACKEND_PORT}/actuator/health" "$BACKEND_LOG" "$BACKEND_PORT" || exit 1
  wait_for_health_or_fail 'Frontend service' FRONTEND_PID "http://127.0.0.1:${FRONTEND_PORT}" "$FRONTEND_LOG" "$FRONTEND_PORT" || exit 1

  echo
  info 'Application running.'
  info "Frontend UI: http://127.0.0.1:${FRONTEND_PORT}"
  info "Backend: http://127.0.0.1:${BACKEND_PORT}"
  info "Swagger UI: http://127.0.0.1:${BACKEND_PORT}/swagger-ui/index.html"
  info "ML API: http://127.0.0.1:${ML_PORT}"
  info 'Seeded login IDs: hq_admin, proc_north, proc_south, site_raj, site_kar'
  info 'Press Ctrl+C to stop all services.'
  echo

  monitor_runtime
}

main "$@"
