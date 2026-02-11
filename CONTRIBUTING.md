# Contributing to Booklore

Thanks for your interest in contributing to Booklore! Whether you're fixing bugs, adding features, improving documentation, or asking questions, every contribution helps.

## What is Booklore?

**Booklore** is a self-hostable digital library platform for managing and reading books and comics.

**Tech Stack:**

- **Frontend:** Angular 20, TypeScript, PrimeNG 19
- **Backend:** Java 21, Spring Boot 3.5
- **Authentication:** Local JWT + optional OIDC (e.g., Authentik)
- **Database:** MariaDB
- **Deployment:** Docker-compatible, reverse proxy-ready

## Table of Contents

- [Where to Start](#where-to-start)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Running Tests](#running-tests)
- [Making Changes](#making-changes)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Code Style](#code-style)
- [Reporting Bugs](#reporting-bugs)
- [Community & Support](#community--support)
- [Code of Conduct](#code-of-conduct)
- [License](#license)

---

## Where to Start

Not sure where to begin? Look for issues labeled:

- [`good first issue`](https://github.com/booklore-app/booklore/labels/good%20first%20issue) - small, well-scoped tasks ideal for newcomers
- [`help wanted`](https://github.com/booklore-app/booklore/labels/help%20wanted) - tasks where maintainers would appreciate a hand

You can also check the [project roadmap](https://github.com/booklore-app/booklore/projects) for larger initiatives.

---

## Getting Started

### Fork and Clone

First, [fork the repository](https://github.com/booklore-app/booklore/fork) on GitHub, then clone your fork locally:

```bash
git clone https://github.com/<your-username>/booklore.git
cd booklore
git remote add upstream https://github.com/booklore-app/booklore.git
```

### Keep Your Fork in Sync

Before starting new work, pull the latest changes from upstream:

```bash
git fetch upstream
git checkout develop
git merge upstream/develop
git push origin develop
```

> **Note:** All work should be based on the `develop` branch, not `main`.

---

## Development Setup

### Project Structure

```
booklore/
├── booklore-ui/             # Angular frontend (TypeScript, PrimeNG)
├── booklore-api/            # Spring Boot backend (Java 21, Gradle)
├── dev.docker-compose.yml   # Development Docker stack
├── assets/                  # Shared assets (logos, icons)
└── local/                   # Local development helpers
```

### Option 1: Docker Development Stack (Recommended)

The fastest way to get a working environment. No local toolchain required.

```bash
docker compose -f dev.docker-compose.yml up
```

This starts:

| Service    | URL / Port            |
|------------|-----------------------|
| Frontend   | http://localhost:4200 |
| Backend    | http://localhost:8080 |
| MariaDB    | localhost:3366        |
| Debug port | localhost:5005        |

All ports are configurable via environment variables (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `REMOTE_DEBUG_PORT`) in the compose file.

```bash
# To stop
docker compose -f dev.docker-compose.yml down
```

### Option 2: Manual Setup

For full control over each component or IDE integration (debugging, hot-reload, etc.).

#### Prerequisites

| Tool          | Version | Download                                     |
|---------------|---------|----------------------------------------------|
| Java          | 21+     | [Adoptium](https://adoptium.net/)            |
| Node.js + npm | 18+     | [nodejs.org](https://nodejs.org/)            |
| MariaDB       | 10.6+   | [mariadb.org](https://mariadb.org/download/) |
| Git           | latest  | [git-scm.com](https://git-scm.com/)         |

#### 1. Database

Start MariaDB and create the database:

```sql
CREATE DATABASE IF NOT EXISTS booklore;
CREATE USER 'booklore_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON booklore.* TO 'booklore_user'@'localhost';
FLUSH PRIVILEGES;
```

> **Tip:** You can also spin up MariaDB via Docker: `docker compose -f local/docker-compose-maria.yml up -d`

#### 2. Backend

Create a dev config at `booklore-api/src/main/resources/application-dev.yml`:

```yaml
app:
  path-book: '/path/to/booklore-data/books'
  path-config: '/path/to/booklore-data/config'

spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://localhost:3306/booklore?createDatabaseIfNotExist=true
    username: booklore_user
    password: your_password
```

Replace the paths with actual directories on your system and ensure they exist with read/write permissions.

```bash
cd booklore-api
./gradlew bootRun --args='--spring.profiles.active=dev'

# Verify
curl http://localhost:8080/actuator/health
```

#### 3. Frontend

```bash
cd booklore-ui
npm install
ng serve
```

The UI will be available at http://localhost:4200 with hot-reload enabled.

> If you hit dependency issues, try `npm install --legacy-peer-deps`.

---

## Running Tests

Always run tests before submitting a pull request.

**Frontend (Vitest):**

```bash
cd booklore-ui
ng test               # Run all tests
ng test --coverage    # With coverage report (output: coverage/)
```

**Backend (JUnit + Gradle):**

```bash
cd booklore-api
./gradlew test                                                        # Run all tests
./gradlew test --tests "com.booklore.api.service.BookServiceTest"     # Specific class
./gradlew test jacocoTestReport                                       # Coverage report
```

---

## Making Changes

### Branch Naming

Create branches from `develop` using these prefixes:

| Prefix      | Use for            | Example                      |
|-------------|--------------------|------------------------------|
| `feat/`     | New features       | `feat/epub-reader-support`   |
| `fix/`      | Bug fixes          | `fix/book-import-validation` |
| `refactor/` | Code restructuring | `refactor/auth-flow`         |
| `docs/`     | Documentation      | `docs/update-install-guide`  |

```bash
git checkout develop
git checkout -b feat/your-feature-name
```

### Commit Messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`

Examples:

```
feat(reader): add keyboard navigation for page turning
fix(api): resolve memory leak in book scanning service
feat(auth)!: migrate to OAuth 2.1

BREAKING CHANGE: OAuth 2.0 is no longer supported
```

### Workflow

1. Create a branch from `develop`
2. Make your changes in small, logical commits
3. Run both frontend and backend tests
4. Update documentation if your changes affect usage
5. Run the linter and fix any issues
6. Push to your fork and open a PR targeting `develop`

---

## Submitting a Pull Request

Before opening your PR:

- [ ] All tests pass (`./gradlew test` and `ng test`)
- [ ] Code follows project conventions (see [Code Style](#code-style))
- [ ] IntelliJ linter shows no errors
- [ ] Branch is up-to-date with `develop`
- [ ] PR description explains *what* changed and *why*
- [ ] PR is linked to a related issue (if applicable)
- [ ] **PR is reasonably sized.** PRs with 1000+ changed lines will be closed without review. Break large changes into small, focused PRs.
- [ ] **For user-facing features:** submit a companion docs PR at [booklore-docs](https://github.com/booklore-app/booklore-docs)

### AI-Assisted Contributions

Contributions using AI tools (Copilot, Claude, ChatGPT, etc.) are welcome, but the quality bar is the same as human-written code. **If you ship it, you own it.**

- **Review every line.** You must be able to explain any part of your change during review.
- **Keep PRs focused.** One feature, one fix, or one refactor per PR.
- **Scrutinize AI-generated tests.** They often pass trivially without asserting anything meaningful.
- **Clean up.** Remove dead code, placeholder comments, empty catch blocks, and unnecessary boilerplate.

---

## Code Style

| Area       | Convention                                                         |
|------------|--------------------------------------------------------------------|
| Angular    | Follow the [official style guide](https://angular.dev/style-guide) |
| Java       | Modern Java 21 features, clean structure                           |
| Formatting | Use IntelliJ IDEA's built-in linter                               |
| UI         | SCSS with PrimeNG components                                       |

---

## Reporting Bugs

1. **Search [existing issues](https://github.com/booklore-app/booklore/issues)** to avoid duplicates.
2. **Open a new issue** with the `bug` label including:
   - Clear, descriptive title (e.g., "Book import fails with PDF files over 100MB")
   - Steps to reproduce
   - Expected vs. actual behavior
   - Screenshots or error logs (if applicable)
   - Environment details (OS, browser, Booklore version)

**Example:**

```
Title: Book metadata not updating after manual edit

Steps to Reproduce:
1. Navigate to any book detail page
2. Click "Edit Metadata"
3. Change the title and click "Save"
4. Refresh the page

Expected: Title should persist after refresh
Actual: Title reverts to original value

Environment: Chrome 120, macOS 14.2, Booklore 1.2.0
```

---

## Community & Support

- **Discord:** [Join the server](https://discord.gg/Ee5hd458Uz) for questions and discussion
- **GitHub Issues:** [Report bugs or request features](https://github.com/booklore-app/booklore/issues)

---

## Code of Conduct

We're committed to providing a welcoming and inclusive environment for everyone.

**Do:**
- Be respectful and considerate
- Welcome newcomers and help them learn
- Accept constructive criticism gracefully
- Focus on what's best for the community

**Don't:**
- Harass, troll, or discriminate
- Make personal attacks or insults
- Publish others' private information

Instances of unacceptable behavior may result in temporary or permanent ban from the project.

---

## License

Booklore is licensed under the [AGPL-3.0 License](./LICENSE). By contributing, you agree that your contributions will be licensed under the same terms.

---

Thank you for being part of the Booklore community!
