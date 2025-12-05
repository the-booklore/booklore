# GitHub Actions Workflows

This document describes all the automated workflows for BookLore.

## Quality Assurance Workflows

### 1. **Build and Test** (`build.yml`)
Runs on: Pull requests to main/develop, manual dispatch
- Frontend unit tests (Angular/Jasmine)
- Backend unit tests (JUnit 5)
- Docker build verification (multi-arch)

**Status**: ✅ Primary test suite

### 2. **Security Scan** (`security.yml`)
Runs on: Pull requests, weekly schedule
- Dependency vulnerability scanning
- Secret scanning (Gitleaks)
- SAST scanning (Semgrep)

**Status**: ✅ Security-focused checks

### 3. **Code Quality & Linting** (`code-quality.yml`)
Runs on: Pull requests to main/develop
- Frontend linting (ESLint)
- Backend code quality (Checkstyle)
- Format validation

**Status**: ✅ Code style enforcement

### 4. **Type Checking & Compilation** (`type-checking.yml`)
Runs on: Pull requests to main/develop
- TypeScript type checking
- Production build verification
- Java compilation check

**Status**: ✅ Compilation validation

### 5. **Database Migration Validation** (`migration-validation.yml`)
Runs on: Pull requests with migration changes
- Flyway migration testing
- Schema validation

**Status**: ✅ Migration safety

### 6. **PR Validation** (`pr-validation.yml`)
Runs on: Pull requests
- PR description quality check (min 50 chars)
- Conventional commit validation
- Auto-labeling based on file changes

**Status**: ✅ PR hygiene

### 7. **Swagger API Tests** (`api-tests.yml`)
Runs on: Pull requests to main/develop, pushes
- Frontend build + bundling
- Backend build with bundled frontend
- API endpoint testing via OpenAPI spec
- Docker integration tests

**Status**: ✅ API validation

## PR Preview & Deployment Workflows

### 8. **PR Preview - Build Multi-Arch Image** (`pr-build.yml`)
Runs on: PR opened/updated
- Builds multi-architecture Docker images (ARM64 + AMD64)
- Caches locally (does NOT push)
- Comments on PR with build status

**Manual Actions**:
- Comment `/publish-pr` to publish to Docker Hub
- Comment `/deploy-pr` to deploy preview

**Status**: ✅ Build staging

### 9. **PR Preview - Publish to Docker Hub** (`pr-publish.yml`)
Runs on: `/publish-pr` comment by authorized user
- Builds and pushes multi-arch image to Docker Hub
- Creates platform-specific tags
- Adds reaction to comment

**Authorized Users**: `balazs-szucs`, `adityachandel`

**Status**: ✅ Docker registry publishing

### 10. **PR Preview - Deploy** (`pr-deploy.yml`)
Runs on: `/deploy-pr` comment by authorized user
- Deploys preview to VPS
- Creates isolated environment per PR
- Updates comment with deployment URL

**Authorized Users**: `balazs-szucs`, `adityachandel`

**Status**: ✅ Preview deployment

### 11. **PR Preview - Cleanup** (`pr-cleanup.yml`)
Runs on: PR closed/merged
- Stops and removes containers
- Deletes volumes and data
- Cleans up deployment files

**Status**: ✅ Cleanup automation

## Release Workflows

### 12. **Push Docker** (`push-docker.yml`)
Runs on: Push to main/develop branches
- Builds and pushes images to Docker Hub
- Tags with version and latest

**Status**: ✅ Production releases

## Workflow Configuration

### Concurrency
All workflows use concurrency groups to cancel in-progress runs when new commits are pushed:
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

### Performance Tips
- Frontend and backend tests run in parallel
- Docker builds use GitHub Actions cache layer
- Security scans run weekly by default (except in PRs)
- Migration validation only runs if migration files changed

## Troubleshooting

### A workflow is failing?
1. Click the failing workflow in GitHub Actions
2. Click the failed job
3. Look for the specific step that failed
4. Check step output for detailed error messages

### Re-running a workflow
- Click "Re-run failed jobs" on the workflow run page
- Or re-run all jobs if needed

### Skipping workflows
Add to commit message:
- `[skip ci]` - Skip all workflows
- `[skip tests]` - Would need custom rules

## Recommended Reading

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)
- [Conventional Commits](https://www.conventionalcommits.org/)

## Future Enhancements

Potential workflows to add:
- Performance benchmarking
- E2E testing (Cypress/Playwright)
- Accessibility testing
- Load testing
- Documentation generation
- Release notes automation
