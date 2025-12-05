# ==========================================
# Stage 1: Build Angular Frontend
# ==========================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Increase Node memory limit for ARM64 builds
ENV NODE_OPTIONS="--max-old-space-size=4096"

# Copy package files and install dependencies
COPY booklore-ui/package*.json ./
RUN npm ci --prefer-offline --no-audit

# Copy source code and build
COPY booklore-ui/ ./
RUN npm run build -- --configuration=production

# ==========================================
# Stage 2: Build Java Backend
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS backend-builder
WORKDIR /build

# Copy Gradle wrapper and settings
COPY booklore-api/gradlew booklore-api/gradlew.bat booklore-api/build.gradle booklore-api/settings.gradle ./
COPY booklore-api/gradle/ gradle/

# Copy Java Source
COPY booklore-api/src/ src/

# Copy Built Frontend Assets into Java Static Resources
# The Angular output path is dist/booklore based on angular.json
COPY --from=frontend-builder /app/frontend/dist/booklore /build/src/main/resources/static/

# Build the JAR (Skip tests here to save time, we test in CI)
RUN ./gradlew clean build -x test --no-daemon

# ==========================================
# Stage 3: Final Runtime Image
# ==========================================
FROM eclipse-temurin:21-jre-alpine

ARG VERSION_TAG=latest
ENV VERSION=${VERSION_TAG}
ENV APP_USER_ID=1000
ENV APP_GROUP_ID=1000

WORKDIR /app

# Install simple utilities if needed (like curl for healthchecks)
RUN apk add --no-cache curl tzdata

# Create necessary directories for BookLore
RUN mkdir -p /app/data /books /bookdrop \
    && chown -R ${APP_USER_ID}:${APP_GROUP_ID} /app /books /bookdrop

# Copy JAR from builder
COPY --from=backend-builder /build/build/libs/*.jar app.jar

# Set the server port to match what we expose
ENV SERVER_PORT=6060

# Expose the configured port
EXPOSE 6060

# Health check - increased start-period for database migrations
HEALTHCHECK --interval=10s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -f http://localhost:6060/actuator/health || exit 1

# Switch to user (using the IDs passed or default 1000)
USER ${APP_USER_ID}:${APP_GROUP_ID}

ENTRYPOINT ["java", "-Xmx1024m", "-jar", "app.jar"]
