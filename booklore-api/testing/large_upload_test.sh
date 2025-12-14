#!/bin/bash
set -e

# large_upload_test.sh
# Tests uploading a large file (200MB) to the API.

API_URL="${1:-http://localhost:8080/api/v1/files/upload/bookdrop}"
ACCESS_TOKEN="${2:-}"
FILE_SIZE_MB=200
FILE_NAME="large_test_file.epub"

echo "Generating ${FILE_SIZE_MB}MB dummy file: $FILE_NAME..."
# Create a dummy file of specific size. 
# Using dd instead of fallocate for better compatibility (e.g. alpine).
dd if=/dev/urandom of="$FILE_NAME" bs=1M count="$FILE_SIZE_MB" status=progress

echo "Uploading $FILE_NAME to $API_URL..."
if [ -n "$ACCESS_TOKEN" ]; then
    echo "Using authentication..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -F "file=@$FILE_NAME" "$API_URL")
else
    echo "No authentication token provided, uploading without auth..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST -F "file=@$FILE_NAME" "$API_URL")
fi

echo "Response Code: $HTTP_CODE"

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "SUCCESS: Large file upload accepted."
    rm "$FILE_NAME"
    exit 0
else
    echo "FAILURE: Server returned $HTTP_CODE"
    rm "$FILE_NAME"
    exit 1
fi
