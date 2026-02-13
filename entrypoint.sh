#!/bin/sh
set -e

USER_ID="${USER_ID:-1000}"
GROUP_ID="${GROUP_ID:-1000}"

# Create group and user if they don't exist
if ! getent group "$GROUP_ID" >/dev/null 2>&1; then
    addgroup -g "$GROUP_ID" -S booklore
fi
if ! getent passwd "$USER_ID" >/dev/null 2>&1; then
    adduser -u "$USER_ID" -G "$(getent group "$GROUP_ID" | cut -d: -f1)" -S -D booklore
fi

exec su-exec "$USER_ID:$GROUP_ID" "$@"
