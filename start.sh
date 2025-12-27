#!/bin/sh

# Set defaults and export so envsubst sees them
: "${BOOKLORE_PORT:=6060}"
: "${BASE_PATH:=}"
export BOOKLORE_PORT
export BASE_PATH

# Normalize BASE_PATH: ensure it starts with / (if not empty) and has no trailing /
if [ -n "$BASE_PATH" ]; then
    # Remove trailing slashes
    BASE_PATH="${BASE_PATH%%/}"
    # Ensure leading slash
    if [ "${BASE_PATH#/}" = "$BASE_PATH" ]; then
        BASE_PATH="/$BASE_PATH"
    fi
    export BASE_PATH
fi

# Use envsubst safely
TMP_CONF="/tmp/nginx.conf.tmp"
envsubst '${BOOKLORE_PORT} ${BASE_PATH}' < /etc/nginx/nginx.conf > "$TMP_CONF"

# Move to final location
mv "$TMP_CONF" /etc/nginx/nginx.conf

# Disable nginx IPv6 listener when IPv6 is disabled on host
[ "$(cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null)" = "0" ] || sed -i '/^[[:space:]]*listen \[\:\:\]:6060;$/d' /etc/nginx/nginx.conf

# Inject BASE_PATH into Angular index.html
if [ -n "$BASE_PATH" ]; then
    # Replace base href with BASE_PATH/ (with trailing slash)
    sed -i "s|<base href=\"/\">|<base href=\"$BASE_PATH/\">|g" /usr/share/nginx/html/index.html
fi

# Start nginx in background
nginx -g 'daemon off;' &

# Start Spring Boot in foreground
su-exec ${USER_ID:-0}:${GROUP_ID:-0} java -jar /app/app.jar
