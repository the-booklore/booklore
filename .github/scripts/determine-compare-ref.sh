#!/bin/bash
# Exit immediately if a command exits with a non-zero status.
set -e
# Exit if any command in a pipeline fails, not just the last one.
set -o pipefail

# The target branch of the pull request (e.g., 'develop', 'master') is passed as the first argument.
TARGET_BRANCH="$1"
echo "🎯 Target branch: $TARGET_BRANCH"

# Handle cases where the target branch is not specified, such as a direct push to a branch.
if [ -z "$TARGET_BRANCH" ]; then
  echo "⚠️ No target branch specified (e.g., a direct push event). Defaulting to compare with 'develop'."
  TARGET_BRANCH="develop"
fi

# Logic to determine the comparison reference based on the target branch.
if [ "$TARGET_BRANCH" = "master" ]; then
  # For PRs to 'master', we compare against the latest git tag.
  # This is common for release workflows where 'master' only contains tagged releases.
  if ! LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null); then
    echo "⚠️ No tags found in repository. Skipping conflict check."
    # Set output to signal the workflow to stop.
    echo "has_ref=false" >> $GITHUB_OUTPUT
    exit 0
  fi
  echo "📌 Comparing against last tag: $LAST_TAG"
  # Set the COMPARE_REF environment variable for subsequent steps in the job.
  echo "COMPARE_REF=$LAST_TAG" >> $GITHUB_ENV
else
  # For all other cases (PRs to 'develop', other feature branches, or direct pushes),
  # we compare against the 'develop' branch.
  echo "🔄 Comparing against head of develop branch"
  # Ensure the local 'develop' branch is up-to-date with the remote.
  git fetch origin develop:develop
  # Set the COMPARE_REF to the remote develop branch.
  echo "COMPARE_REF=origin/develop" >> $GITHUB_ENV
fi

# Set a GitHub Actions output variable to indicate that a valid comparison ref was found.
echo "has_ref=true" >> $GITHUB_OUTPUT
