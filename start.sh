#!/usr/bin/env sh

# Disable nginx IPv6 listener when IPv6 is disabled on host
[ "$(cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null)" = "0" ] || sed -i '/^[[:space:]]*listen \[\:\:\]:6060;$/d' /etc/nginx/nginx.conf

# Start nginx in background
nginx -g 'daemon off;' &

# Start Spring Boot in foreground
exec java -jar /app/app.jar
