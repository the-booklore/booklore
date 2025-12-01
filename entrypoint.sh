#!/bin/sh
set -e

# Create a group and user with the provided IDs, defaulting to 1000
TARGET_UID=${USER_ID:-1000}
TARGET_GID=${GROUP_ID:-1000}

# Check if group exists, create if not
if ! getent group ${TARGET_GID} >/dev/null 2>&1; then
    addgroup -g ${TARGET_GID} bookloregroup
fi

# Check if user exists, create if not
if ! getent passwd ${TARGET_UID} >/dev/null 2>&1; then
    adduser -D -u ${TARGET_UID} -G bookloregroup booklore
fi

# Ensure ownership of app files
chown -R ${TARGET_UID}:${TARGET_GID} /app

# Execute the main command as the new user
exec su-exec ${TARGET_UID}:${TARGET_GID} "$@"

