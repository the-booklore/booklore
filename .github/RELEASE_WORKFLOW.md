# Release Workflow

This document describes the branch strategy, CI/CD pipelines, and release process for Booklore.

## Branch Strategy

```
master          Stable releases only. Tagged with vX.Y.Z
   ↑
release/X.Y     Release stabilization. RC builds published here
   ↑
develop         Active development. All feature PRs merge here
```

| Branch | Purpose | Stability |
|--------|---------|-----------|
| `master` | Production releases | Stable |
| `release/X.Y` | Release candidates, bug fixes | Stabilizing |
| `develop` | New features, active development | Unstable |

## CI/CD Pipelines

### 1. Develop Pipeline (`develop-pipeline.yml`)

**Triggers:**
- Push to `develop`
- PRs targeting `develop`, `release/**`, or `master`

**Actions:**
- Flyway migration check
- Backend tests (Gradle)
- Frontend tests (Angular/Vitest)
- Docker build and push (for pushes and internal PRs)

**Image Tags:** `develop-{sha}`, `pr-{number}-{sha}`

---

### 2. Release Pipeline (`release-pipeline.yml`)

**Triggers:**
- Push to `release/**` branches
- PRs targeting `release/**` branches

**Actions:**
- Flyway migration check (against `master`)
- Backend and frontend tests
- Docker build and push with RC tags

**Image Tags:** `v1.18.0-rc.1`, `v1.18.0-rc.2`, `release-1.18-{sha}`

**Validations:**
- Branch name must be `release/X.Y` or `release/X.Y.Z`
- Version must be newer than latest tag
- Rejects suspicious version jumps (typo protection)

---

### 3. Master Tag Pipeline (`master-pipeline.yml`)

**Triggers:**
- Push of tags matching `v*`

**Actions:**
- Tag format validation
- Flyway migration check
- Backend and frontend tests
- Docker build and push
- GitHub Release publication (publishes the draft release)

**Image Tags:** `v1.18.0`, `latest` (stable releases only)

**Validations:**
- Tag must be valid semver (`vX.Y.Z` or `vX.Y.Z-prerelease.N`)
- Version must be newer than latest stable tag
- Rejects suspicious version jumps (typo protection)
- Blocks prereleases if stable version already exists

---

### 4. Draft Release Pipeline (`draft-release.yml`)

**Triggers:**
- Push to `master`

**Actions:**
- Updates draft GitHub Release with changelog from merged PRs
- Categorizes changes based on PR labels (feature, bug, enhancement, etc.)
- Auto-resolves next version based on labels (major/minor/patch)

**Configuration:** `.github/release-drafter.yml`

**How it works:**
1. Each PR merged to `master` triggers this workflow
2. Release-drafter accumulates changes into a draft release
3. When you push a `v*` tag, the master-pipeline publishes the draft

**Label → Version mapping:**
| Label | Version Bump |
|-------|--------------|
| `major` | X.0.0 |
| `minor` | 0.X.0 |
| `patch` | 0.0.X |

**Label → Changelog category:**
| Labels | Category |
|--------|----------|
| `feature` | New Features |
| `enhancement` | Enhancements |
| `bug`, `fix` | Bug Fixes |
| `refactor`, `cleanup`, `chore` | Refactoring & Maintenance |
| `dependencies`, `deps` | Dependencies |
| `ci`, `cd`, `workflow` | CI/CD |
| `docs`, `documentation` | Documentation |

PRs with the `skip changelog` label are excluded from release notes.

---

## Release Flow Diagram

```
v1.17.0 (current)
    │
develop ────●────●────●────●─────────────────●──▶ (continues)
            │                                 ↑
            │ (1) cut branch                  │ (5) merge back
            ▼                                 │
       release/1.18 ──●──●──●────────────────┼──▶ (delete)
                      │  │  │                │
                      ▼  ▼  ▼                │
                   rc.1 rc.2 rc.3            │
                                             │
master ──────────────────────────────────────●──▶
                                             │
                                        (4) tag v1.18.0
                                             │
                                             ▼
                                    Docker: v1.18.0 + latest
                                    GitHub Release created
```

## Step-by-Step Release Process

### Phase 1: Cut Release Branch

When `develop` is feature-complete for the release:

```bash
git checkout develop && git pull
git checkout -b release/1.18
git push -u origin release/1.18
```

**What happens:**
- Release pipeline runs tests
- Publishes `v1.18.0-rc.1` to Docker Hub and GHCR
- RC number increments with each subsequent push

---

### Phase 2: Stabilize (Optional)

If bugs are found during RC testing, fix them on the release branch:

```bash
git checkout release/1.18
git checkout -b fix/critical-bug
# ... make fixes ...
git commit -m "Fix critical bug in authentication"
git push -u origin fix/critical-bug

# Create PR targeting the release branch
gh pr create --base release/1.18 --title "Fix critical bug"
gh pr merge --squash
```

**What happens:**
- Each merge to `release/1.18` publishes a new RC (`v1.18.0-rc.2`, `rc.3`, etc.)
- Testers can pull specific RC versions to validate fixes

**Recommended: Keep `develop` in sync**

To avoid `develop` having stale/buggy code until the release completes, create PRs targeting both branches:

```bash
git checkout release/1.18
git checkout -b fix/critical-bug
# ... make fixes ...
git commit -m "Fix critical bug in authentication"
git push -u origin fix/critical-bug

# Create PR for release branch
gh pr create --base release/1.18 --title "Fix critical bug"

# Create PR for develop (same branch, different target)
gh pr create --base develop --title "Fix critical bug"
```

Both PRs can be reviewed and merged independently. When `master` is merged back to `develop` in Phase 5, Git will recognize the identical changes and resolve cleanly.

---

### Phase 3: Merge to Master

When the RC is validated and stable:

```bash
# Create PR from release branch to master
gh pr create --base master --head release/1.18 --title "Release v1.18.0"

# After approval, merge (use merge commit, not squash)
gh pr merge --merge
```

**What happens:**
- Develop pipeline runs tests on the PR
- Merge commit lands on `master`
- No release is published yet (waiting for tag)

---

### Phase 4: Tag and Release

Create the release tag to trigger the final release:

```bash
git checkout master && git pull
git tag v1.18.0
git push origin v1.18.0
```

**What happens:**
- Master Tag pipeline validates the tag
- Runs full test suite
- Builds and pushes Docker images: `v1.18.0` + `latest`
- Publishes the draft GitHub Release (created by `draft-release.yml`) with changelog

---

### Phase 5: Post-Release Cleanup

Sync the release back to `develop` and clean up:

```bash
# Merge master back to develop (includes any RC fixes)
git checkout develop && git pull
git merge master
git push origin develop

# Delete the release branch
git push origin --delete release/1.18
git branch -d release/1.18
```

---

## Quick Reference Commands

### New Minor Release (v1.17.0 → v1.18.0)

```bash
# Cut release
git checkout develop && git pull
git checkout -b release/1.18
git push -u origin release/1.18

# When stable, merge to master
gh pr create --base master --head release/1.18 --title "Release v1.18.0"
gh pr merge --merge

# Tag release
git checkout master && git pull
git tag v1.18.0
git push origin v1.18.0

# Cleanup
git checkout develop && git pull && git merge master && git push
git push origin --delete release/1.18
git branch -d release/1.18
```

### New Patch Release (v1.18.0 → v1.18.1)

```bash
# Cut release from master (or existing release branch)
git checkout master && git pull
git checkout -b release/1.18
git push -u origin release/1.18

# Cherry-pick or fix bugs, then merge and tag
gh pr create --base master --head release/1.18 --title "Release v1.18.1"
gh pr merge --merge
git checkout master && git pull
git tag v1.18.1
git push origin v1.18.1

# Cleanup
git checkout develop && git pull && git merge master && git push
git push origin --delete release/1.18
```

### Hotfix (urgent production fix)

```bash
# Create hotfix branch from master
git checkout master && git pull
git checkout -b hotfix/1.18.1
# ... make urgent fix ...
git commit -m "Fix critical security issue"
git push -u origin hotfix/1.18.1

# Merge to master and tag
gh pr create --base master --head hotfix/1.18.1 --title "Hotfix v1.18.1"
gh pr merge --merge
git checkout master && git pull
git tag v1.18.1
git push origin v1.18.1

# Merge fix to develop
git checkout develop && git pull && git merge master && git push
git push origin --delete hotfix/1.18.1
```

---

## Version Validation Rules

The pipelines enforce these rules to prevent mistakes:

| Rule | Example | Result |
|------|---------|--------|
| No backwards versions | `v1.17.0` after `v1.18.0` | Blocked |
| No duplicate stable versions | `v1.18.0` after `v1.18.0` | Blocked |
| No prerelease after stable | `v1.18.0-rc.1` after `v1.18.0` | Blocked |
| Major jump ≤ 1 | `v3.0.0` after `v1.18.0` | Blocked |
| Minor jump ≤ 5 | `v1.25.0` after `v1.18.0` | Blocked |
| Patch jump ≤ 10 | `v1.18.15` after `v1.18.0` | Blocked |

These rules catch typos like `release/1.118` instead of `release/1.18`.

---

## Docker Image Tags

| Event | Tags Published |
|-------|---------------|
| Push to `develop` | `develop-abc1234` |
| PR #42 | `pr-42-abc1234` |
| Push to `release/1.18` | `v1.18.0-rc.1`, `release-1.18-abc1234` |
| Tag `v1.18.0` | `v1.18.0`, `latest` |
| Tag `v1.18.1-rc.1` | `v1.18.1-rc.1` (no `latest`) |

---

## FAQ

**Q: Can I have multiple release branches simultaneously?**
A: Yes. `release/1.18` and `release/1.19` can coexist. Each gets independent RC numbers.

**Q: What if I need to release a patch for an older version?**
A: Create `release/1.17` from the `v1.17.0` tag, make fixes, and tag `v1.17.1`.

**Q: Why use merge commits instead of squash for release PRs?**
A: Merge commits preserve the full history of RC fixes, making it easier to track what changed.

**Q: Can I skip the RC phase for urgent fixes?**
A: Yes. For hotfixes, you can merge directly to master and tag. The release pipeline is optional.

**Q: What happens if I push a tag that fails validation?**
A: The pipeline fails before publishing anything. Delete the tag (`git push --delete origin v1.18.0`), fix the issue, and re-tag.

**Q: How do I preview release notes before publishing?**
A: Go to GitHub Releases. The draft release is automatically updated as PRs merge to `master`. Review and edit the draft before tagging.

**Q: Why don't I see changes in the draft release?**
A: Ensure PRs have appropriate labels (`feature`, `bug`, `enhancement`, etc.). PRs without recognized labels still appear but won't be categorized. PRs with `skip changelog` label are excluded.
