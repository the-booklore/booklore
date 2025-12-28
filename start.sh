#!/usr/bin/env sh

# Start nginx in background
nginx -g 'daemon off;' &

# Start Spring Boot in foreground
exec java -jar /app/app.jar
