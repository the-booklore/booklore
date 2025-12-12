#!/bin/bash
set -euo pipefail

TARGET_BRANCH="${1:-origin/develop}"
MIGRATION_DIR="booklore-api/src/main/resources/db/migration"

echo "=== Flyway Migration Integrity Check ==="

# Fetch the target branch properly
REMOTE_BRANCH="${TARGET_BRANCH#origin/}"
echo "Fetching origin/$REMOTE_BRANCH..."
git fetch origin "$REMOTE_BRANCH" --quiet

# Get migration files from base branch
mapfile -t BASE_FILES < <(git ls-tree -r --name-only "$TARGET_BRANCH" -- "$MIGRATION_DIR" 2>/dev/null | grep -E "\.sql$" || true)

if [[ ${#BASE_FILES[@]} -eq 0 ]]; then
    echo "ℹ️  No existing migrations in $TARGET_BRANCH"
    exit 0
fi

EXIT_CODE=0

for FILE in "${BASE_FILES[@]}"; do
    # Skip repeatable migrations (R__ prefix) - they CAN be modified
    if [[ "$(basename "$FILE")" =~ ^R__ ]]; then
        echo "⏭️  Skipping repeatable: $FILE"
        continue
    fi

    # Check existence
    if [[ ! -f "$FILE" ]]; then
        echo "❌ DELETED: $FILE"
        echo "   → Existing versioned migrations must not be deleted"
        EXIT_CODE=1
        continue
    fi

    # Check content integrity
    BASE_HASH=$(git show "$TARGET_BRANCH:$FILE" 2>/dev/null | sha256sum | cut -d' ' -f1)
    CURRENT_HASH=$(sha256sum "$FILE" | cut -d' ' -f1)

    if [[ "$BASE_HASH" != "$CURRENT_HASH" ]]; then
        echo "❌ MODIFIED: $FILE"
        echo "   → Create a new migration instead of modifying existing ones"
        EXIT_CODE=1
    else
        echo "✅ OK: $FILE"
    fi
done

# Validate new migrations follow naming convention
echo ""
echo "=== Checking New Migrations ==="
mapfile -t NEW_FILES < <(git diff --name-only --diff-filter=A "$TARGET_BRANCH" -- "$MIGRATION_DIR" | grep -E "\.sql$" || true)

for FILE in "${NEW_FILES[@]}"; do
    BASENAME=$(basename "$FILE")
    # Check naming: V{version}__{description}.sql or R__{description}.sql
    if [[ ! "$BASENAME" =~ ^V[0-9]+(\.[0-9]+)*__[A-Za-z0-9_-]+\.sql$ ]] && \
       [[ ! "$BASENAME" =~ ^R__[A-Za-z0-9_-]+\.sql$ ]]; then
        echo "❌ INVALID NAME: $FILE"
        echo "   → Expected: V{version}__{description}.sql or R__{description}.sql"
        EXIT_CODE=1
    else
        echo "✅ NEW: $FILE"
    fi
done

# Check for version conflicts
echo ""
echo "=== Checking for Version Conflicts ==="
ALL_VERSIONS=$(find "$MIGRATION_DIR" -name "V*.sql" -exec basename {} \; | sed 's/__.*$//' | sort)
DUPLICATES=$(echo "$ALL_VERSIONS" | uniq -d)

if [[ -n "$DUPLICATES" ]]; then
    echo "❌ DUPLICATE VERSIONS FOUND:"
    echo "$DUPLICATES"
    EXIT_CODE=1
fi

echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    echo "✅ Migration integrity check passed"
else
    echo "❌ Migration integrity check failed"
fi

exit $EXIT_CODE
