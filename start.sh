#!/bin/sh

# Set default and export so envsubst sees it
: "${BOOKLORE_PORT:=6060}"
export BOOKLORE_PORT

# Use envsubst safely
TMP_CONF="/tmp/nginx.conf.tmp"
envsubst '${BOOKLORE_PORT}' < /etc/nginx/nginx.conf > "$TMP_CONF"

# Move to final location
mv "$TMP_CONF" /etc/nginx/nginx.conf

# Start nginx in background
nginx -g 'daemon off;' &

# Start Spring Boot in foreground
su-exec ${USER_ID:-0}:${GROUP_ID:-0} java -jar /app/app.jar