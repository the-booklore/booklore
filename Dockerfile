# ==========================================
# Stage 1: Build the Angular Frontend
# ==========================================
FROM node:22-alpine AS angular-build

WORKDIR /angular-app

# Copy dependency files first for better layer caching
COPY ./booklore-ui/package.json ./booklore-ui/package-lock.json ./

# Increase Node memory for ARM64 builds
ENV NODE_OPTIONS="--max-old-space-size=4096"

# Install dependencies (cached layer if package*.json unchanged)
RUN npm ci --prefer-offline --no-audit

# Copy source code and build
COPY ./booklore-ui/ ./
RUN npm run build -- --configuration=production

# ==========================================
# Stage 2: Build the Spring Boot Backend
# ==========================================
FROM gradle:8.14.3-jdk21-alpine AS springboot-build

WORKDIR /springboot-app

# Copy Gradle wrapper and config first for dependency caching
COPY ./booklore-api/gradle ./gradle
COPY ./booklore-api/gradlew ./booklore-api/gradlew.bat ./
COPY ./booklore-api/build.gradle ./booklore-api/settings.gradle ./

# Download dependencies (cached layer if build.gradle unchanged)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY ./booklore-api/src ./src

# Inject version into application.yaml using yq
ARG APP_VERSION
RUN apk add --no-cache yq && \
    yq eval '.app.version = strenv(APP_VERSION)' -i ./src/main/resources/application.yaml

# Build the JAR (skip tests - run separately in CI)
RUN ./gradlew clean bootJar -x test --no-daemon

# ==========================================
# Stage 3: Final Runtime Image
# ==========================================
FROM eclipse-temurin:21.0.9_10-jre-alpine

ARG APP_VERSION
ARG APP_REVISION

# Set OCI labels
LABEL org.opencontainers.image.title="BookLore" \
      org.opencontainers.image.description="BookLore: A self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo & KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.url="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.documentation="https://booklore-app.github.io/booklore-docs/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:21.0.9_10-jre-alpine"

RUN apk update && apk add nginx gettext su-exec

COPY ./nginx.conf /etc/nginx/nginx.conf
COPY --from=angular-build /angular-app/dist/booklore/browser /usr/share/nginx/html
COPY --from=springboot-build /springboot-app/build/libs/booklore-api-0.0.1-SNAPSHOT.jar /app/app.jar
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8080 80

CMD ["/start.sh"]
