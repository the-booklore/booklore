#!/bin/bash
set -euo pipefail

# =======================
# Multi-arch Docker Build & Push for Booklore
# =======================

# Ensure a version/tag is passed
if [ -z "${1:-}" ]; then
  echo "ERROR: You must provide a version/tag as the first argument."
  echo "Usage: $0 <version-tag>"
  exit 1
fi

VERSION="$1"

IMAGE_REF="ghcr.io/the-booklore/booklore:$VERSION"

echo "Building Booklore App with multi-arch version: $VERSION"
echo "Target registry image: $IMAGE_REF"

# Ensure Docker Buildx builder exists and is used
docker buildx create --use --name multiarch-builder || true

# Build and push multi-arch Docker image
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t "$IMAGE_REF" \
  --push \
  .

echo "Multi-arch Docker image $IMAGE_REF pushed successfully!"
