#!/bin/bash
# Exit immediately if a command exits with a non-zero status.
set -e
# Exit if any command in a pipeline fails, not just the last one.
set -o pipefail

# Define the path where Flyway migration files are stored.
MIGRATION_PATH="booklore-api/src/main/resources/db/migration"

# --- Part 1: Check for duplicate versions within the PR branch itself ---

# Get ALL migration files in the current HEAD of the PR branch for an internal duplicate check.
find "$MIGRATION_PATH" -type f -name "V*.sql" > /tmp/all_pr_files.txt

# Check for duplicate versions within the PR branch. This prevents merging a branch
# that contains multiple files with the same version number.
echo "🔎 Checking for duplicate versions in the branch..."
> /tmp/versions_all_pr.txt
# Loop through all found migration files and extract their version numbers.
while IFS= read -r file; do
  filename=$(basename "$file")
  # sed extracts the version number (e.g., 1.0.0) from a filename like 'V1.0.0__description.sql'.
  version=$(echo "$filename" | sed -n 's/^V\([0-9.]*\)__.*/\1/p')
  [ -n "$version" ] && echo "$version" >> /tmp/versions_all_pr.txt
done < /tmp/all_pr_files.txt

# 'uniq -d' filters for lines that appear more than once in the sorted list.
sort /tmp/versions_all_pr.txt | uniq -d > /tmp/duplicates_in_pr.txt

# If the duplicates file is not empty, report the error and exit.
if [ -s /tmp/duplicates_in_pr.txt ]; then
  echo "❌ Duplicate migration versions found within the branch!"
  echo ""
  echo "The following versions are duplicated:"
  while IFS= read -r version; do
    echo "  - Version V$version is used by:"
    # Show the conflicting files for easy debugging.
    grep "V${version}__" /tmp/all_pr_files.txt | xargs -n1 basename | sed 's/^/    /'
  done < /tmp/duplicates_in_pr.txt
  echo ""
  echo "💡 To fix: Ensure all migration files have a unique version number."
  exit 1
fi

echo "✅ No duplicate versions found within the branch."

# --- Part 2: Extract versions from NEWLY ADDED files for conflict checking against the base branch ---

# /tmp/pr_files.txt is created by analyze-changes.sh and contains only ADDED files.
# If the file doesn't exist or is empty, there's nothing to check.
if [ ! -f /tmp/pr_files.txt ] || [ ! -s /tmp/pr_files.txt ]; then
    echo "ℹ️ No new migration files to check for conflicts."
    # Set output to signal the workflow to skip the conflict check step.
    echo "has_versions=false" >> $GITHUB_OUTPUT
    exit 0
fi

echo "🔎 Extracting versions from new files..."
> /tmp/versions_pr.txt
# Loop through only the NEWLY ADDED files and extract their versions.
while IFS= read -r file; do
  filename=$(basename "$file")
  version=$(echo "$filename" | sed -n 's/^V\([0-9.]*\)__.*/\1/p')
  [ -n "$version" ] && echo "$version" >> /tmp/versions_pr.txt
done < /tmp/pr_files.txt

# If no valid versions were extracted from the new files, exit.
if [ ! -s /tmp/versions_pr.txt ]; then
    echo "ℹ️ No versions found in new migration files."
    echo "has_versions=false" >> $GITHUB_OUTPUT
    exit 0
fi

# Create a sorted, unique list of versions from the new files.
# This file will be used by 'check-conflicts.sh'.
sort -u /tmp/versions_pr.txt > /tmp/versions_pr_unique.txt

# Set output to signal that there are new versions to check for conflicts.
echo "has_versions=true" >> $GITHUB_OUTPUT
