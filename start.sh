#!/bin/sh

# Set default and export so envsubst sees it
: "${BOOKLORE_PORT:=6060}"
export BOOKLORE_PORT

# Use envsubst safely
TMP_CONF="/tmp/nginx.conf.tmp"
envsubst '${BOOKLORE_PORT}' < /etc/nginx/nginx.conf > "$TMP_CONF"

# Move to final location
mv "$TMP_CONF" /etc/nginx/nginx.conf

# Disable nginx IPv6 listener when IPv6 is disabled on host
[ "$(cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null)" = "0" ] || sed -i '/^[[:space:]]*listen \[\:\:\]:6060;$/d' /etc/nginx/nginx.conf

# Start nginx in background
nginx -g 'daemon off;' &

# Start Spring Boot in foreground
su-exec ${USER_ID:-0}:${GROUP_ID:-0} java -jar /app/app.jar
