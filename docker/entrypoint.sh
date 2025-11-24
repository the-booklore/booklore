#!/usr/bin/env bash
set -euo pipefail

BOOKLORE_PORT="${BOOKLORE_PORT:-6060}"

render_nginx_config() {
  if [[ -f /etc/nginx/nginx.conf.template ]]; then
    envsubst '${BOOKLORE_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
  fi
}

shutdown() {
  if [[ -n "${JAVA_PID:-}" ]]; then
    kill "${JAVA_PID}" 2>/dev/null || true
  fi
  if [[ -n "${NGINX_PID:-}" ]]; then
    kill "${NGINX_PID}" 2>/dev/null || true
  fi
  wait || true
  exit 0
}

trap shutdown SIGINT SIGTERM

render_nginx_config

java ${JAVA_OPTS:-} -jar /opt/booklore/app.jar &
JAVA_PID=$!

nginx -g 'daemon off;' &
NGINX_PID=$!

wait -n "${JAVA_PID}" "${NGINX_PID}"
shutdown

