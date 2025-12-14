#!/bin/bash
# Exit immediately if a command exits with a non-zero status.
set -e
# Exit if any command in a pipeline fails, not just the last one.
set -o pipefail

# Define the path where Flyway migration files are stored.
MIGRATION_PATH="booklore-api/src/main/resources/db/migration"

# Get ALL changes: Added (A), Modified (M), Renamed (R), Copied (C), Deleted (D)
# for SQL files in the migration path between the comparison ref and the current HEAD.
# The output is saved to a temporary file for further processing.
git diff --name-status --diff-filter=AMRCD $COMPARE_REF...HEAD -- "$MIGRATION_PATH/V*.sql" > /tmp/all_changes.txt

# The check for no changes is now handled in the workflow.
# If this script runs, it's because changes were detected.

echo "📝 Migration changes detected:"
# Display the detected changes, indented for readability.
cat /tmp/all_changes.txt | sed 's/^/  /'
echo ""

# Check for deleted files
# Grep for lines starting with 'D' (Deleted). The '|| true' prevents the script from exiting if no matches are found.
DELETED=$(grep "^D" /tmp/all_changes.txt || true)
if [ -n "$DELETED" ]; then
  echo "❌ ERROR: Deleted migration files detected!"
  echo "$DELETED" | sed 's/^/  /'
  echo ""
  echo "Flyway migrations should NEVER be deleted after being applied."
  echo "If you need to revert changes, create a new migration."
  exit 1
fi

# Check for renamed files
# Grep for lines starting with 'R' (Renamed).
RENAMED=$(grep "^R" /tmp/all_changes.txt || true)
if [ -n "$RENAMED" ]; then
  echo "❌ ERROR: Renamed migration files detected!"
  echo "$RENAMED" | sed 's/^/  /'
  echo ""
  echo "Flyway migrations should NEVER be renamed after being applied."
  echo "This will cause issues with migration history tracking."
  echo ""
  echo "💡 To fix: Revert the rename and create a new migration file instead."
  exit 1
fi

# Check for modified files
# Grep for lines starting with 'M' (Modified).
MODIFIED=$(grep "^M" /tmp/all_changes.txt || true)
if [ -n "$MODIFIED" ]; then
  echo "❌ ERROR: Modified migration files detected!"
  echo "$MODIFIED" | sed 's/^/  /'
  echo ""
  echo "Flyway migrations should NEVER be modified after being applied."
  echo "This will cause checksum validation failures in environments where it has already been applied."
  echo ""
  echo "💡 To fix: Revert the changes and create a new migration file instead."
  exit 1
fi

# Extract ADDED files for conflict checking in a later step.
# We grep for lines starting with 'A' (Added), then use 'cut' to get just the file path.
# 'touch' ensures the file exists even if there are no added files.
grep "^A" /tmp/all_changes.txt | cut -f2- > /tmp/pr_files.txt || touch /tmp/pr_files.txt

# Set a GitHub Actions output variable to indicate that migration changes were found.
# This is used by the workflow to decide whether to run subsequent steps.
echo "has_changes=true" >> $GITHUB_OUTPUT
