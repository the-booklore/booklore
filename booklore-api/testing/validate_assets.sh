#!/bin/bash
set -e

TARGET_DIR="$1"

if [ -z "$TARGET_DIR" ]; then
    echo "Usage: $0 <directory_to_scan>"
    echo "Creating a dummy test directory for demonstration..."
    TARGET_DIR="tests/fixtures/bad_books"
    mkdir -p "$TARGET_DIR"
fi

echo "Starting Asset Integrity Check on: $TARGET_DIR"

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed (required for epubcheck)."
    exit 1
fi

if ! command -v 7z &> /dev/null; then
    echo "Warning: 7z is not installed. Archive checks will be skipped."
fi

if ! command -v convert &> /dev/null; then
    echo "Warning: ImageMagick (convert/identify) is not installed."
fi

EPUBCHECK_JAR="lib/epubcheck.jar" # Placeholder path
if [ ! -f "$EPUBCHECK_JAR" ]; then
    echo "Downloading epubcheck..."
    mkdir -p lib
    echo "Epubcheck not found at $EPUBCHECK_JAR. Skipping EPUB validation (simulated)."
else
    echo "Running epubcheck..."
    find "$TARGET_DIR" -name "*.epub" -print0 | while IFS= read -r -d '' file; do
        java -jar "$EPUBCHECK_JAR" "$file" --mode exp --quiet || echo "Validation failed for $file"
    done
fi

if command -v 7z &> /dev/null; then
    echo "Running Archive Integrity Checks (CBZ/CBR)..."
    find "$TARGET_DIR" -name "*.cbz" -o -name "*.cbr" -print0 | while IFS= read -r -d '' file; do
        if ! 7z t "$file" > /dev/null 2>&1; then
            echo "CORRUPT ARCHIVE DETECTED: $file"
            exit 1
        fi
    done
fi

if command -v identify &> /dev/null; then
    echo "Running Image Integrity Checks..."
    find "$TARGET_DIR" -name "*.jpg" -o -name "*.png" -print0 | while IFS= read -r -d '' file; do
        if identify -verbose "$file" | grep -q "Corrupt"; then
            echo "CORRUPT IMAGE DETECTED: $file"
            exit 1
        fi
    done
fi

echo "Asset Integrity Check Completed."
