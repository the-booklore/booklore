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

# Fix ownership of app data when UID/GID changes (e.g., upgrading from pre-2.0 where default was root)
if [ -d /app/data ]; then
    CURRENT_UID="$(stat -c '%u' /app/data 2>/dev/null)"
    CURRENT_GID="$(stat -c '%g' /app/data 2>/dev/null)"
    if [ "$CURRENT_UID" != "$USER_ID" ] || [ "$CURRENT_GID" != "$GROUP_ID" ]; then
        chown -R "$USER_ID:$GROUP_ID" /app/data 2>/dev/null || true
    fi
fi

exec su-exec "$USER_ID:$GROUP_ID" "$@"
