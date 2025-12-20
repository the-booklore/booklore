#!/bin/bash
# Exit immediately if a command exits with a non-zero status.
set -e
# Exit if any command in a pipeline fails, not just the last one.
set -o pipefail

# If there are no new versions to check, exit gracefully.
# This file is created by 'validate-versions.sh'.
# This can happen if a PR has changes, but none are new migration files.
if [ ! -s /tmp/versions_pr_unique.txt ]; then
  echo "ℹ️ No new migration versions to check for conflicts."
  exit 0
fi

# Define the path where Flyway migration files are stored.
MIGRATION_PATH="booklore-api/src/main/resources/db/migration"

echo "🔍 Fetching migration files from $COMPARE_REF..."

# Get ALL existing migration files from the comparison ref (e.g., 'develop' or a tag).
# 'git ls-tree' lists the contents of a tree object.
# The output is piped to grep to filter for only Flyway SQL files.
# '|| touch' ensures the temp file exists even if no files are found.
git ls-tree -r --name-only $COMPARE_REF -- "$MIGRATION_PATH/" 2>/dev/null | \
  grep "V.*\.sql$" > /tmp/base_files.txt || touch /tmp/base_files.txt

# Handle the case where no migration files exist in the base branch.
if [ ! -s /tmp/base_files.txt ]; then
  echo "⚠️ No migration files found in $COMPARE_REF"
  echo "This might be the first migration or the path has changed."
  echo ""
  echo "✅ Skipping version conflict check."

  PR_COUNT=$(wc -l < /tmp/versions_pr_unique.txt)
  echo ""
  echo "📊 Migration Summary:"
  echo "  - Existing migrations in $COMPARE_REF: 0"
  echo "  - New migrations in this PR: $PR_COUNT"
  exit 0
fi

echo "📋 Found $(wc -l < /tmp/base_files.txt) migration files in $COMPARE_REF"

# Extract versions from the base files.
# The loop reads each file path, extracts the version number from the filename,
# and appends it to a temporary file.
> /tmp/versions_base.txt
while IFS= read -r file; do
  filename=$(basename "$file")
  # sed extracts the version number (e.g., 1.0.0) from a filename like 'V1.0.0__description.sql'.
  version=$(echo "$filename" | sed -n 's/^V\([0-9.]*\)__.*/\1/p')
  [ -n "$version" ] && echo "$version" >> /tmp/versions_base.txt
done < /tmp/base_files.txt

# Create a file with only unique, sorted version numbers from the base.
sort -u /tmp/versions_base.txt > /tmp/versions_base_unique.txt

BASE_COUNT=$(wc -l < /tmp/versions_base_unique.txt)
echo "📊 Found $BASE_COUNT unique versions in $COMPARE_REF"

# Find conflicts between base versions and versions from NEW PR files.
# 'comm -12' finds lines common to both sorted files.
CONFLICTS=$(comm -12 /tmp/versions_base_unique.txt /tmp/versions_pr_unique.txt)

# If conflicts are found, report them and exit with an error.
if [ -n "$CONFLICTS" ]; then
  echo "❌ Version conflicts detected!"
  echo ""
  echo "The following versions from your new migration files already exist in $COMPARE_REF:"
  echo "$CONFLICTS" | sed 's/^/  V/'
  echo ""

  # Show which files have conflicting versions for easier debugging.
  echo "Conflicting files:"
  while IFS= read -r version; do
    echo "  Version V$version exists in:"
    grep "V${version}__" /tmp/base_files.txt | xargs -n1 basename | sed 's/^/    BASE: /'
    # /tmp/pr_files.txt contains only added files from the PR (from analyze-changes.sh).
    grep "V${version}__" /tmp/pr_files.txt | xargs -n1 basename | sed 's/^/    PR:   /'
  done <<< "$CONFLICTS"

  echo ""
  echo "💡 To fix: Use a version number that doesn't exist in $COMPARE_REF"
  exit 1
fi

echo "✅ No version conflicts detected."

# Get the count of new migrations in the PR.
PR_COUNT=$(wc -l < /tmp/versions_pr_unique.txt)

# Print a final summary.
echo ""
echo "📊 Migration Summary:"
echo "  - Existing migrations in $COMPARE_REF: $BASE_COUNT"
echo "  - New migrations in this PR: $PR_COUNT"
