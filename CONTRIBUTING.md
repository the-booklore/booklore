# Contributing to Booklore

🎉 **Thank you for your interest in contributing to Booklore!** Whether you're fixing bugs, adding features, improving documentation, or simply asking questions, every contribution helps make Booklore better for everyone.

---

## 📚 What is Booklore?

**Booklore** is a modern, self-hostable digital library platform for managing and reading books and comics. It's designed with privacy, flexibility, and ease of use in mind.

**Tech Stack:**
- **Frontend**: Angular 20, TypeScript, PrimeNG 19
- **Backend**: Java 21, Spring Boot 3.5
- **Authentication**: Local JWT + optional OIDC (e.g., Authentik)
- **Database**: MariaDB
- **Deployment**: Docker-compatible, reverse proxy-ready

---

## 📦 Project Structure

```
booklore/
├── booklore-ui/           # Angular frontend application
├── booklore-api/          # Spring Boot backend API
├── assets/                # Shared assets (logos, icons, etc.)
├── docker-compose.yml     # Production Docker setup
└── dev.docker-compose.yml # Development Docker setup
```

---

## 🚀 Getting Started

### 1. Fork and Clone

First, fork the repository to your GitHub account, then clone it locally:

```bash
# Clone your fork
git clone https://github.com/<your-username>/booklore.git
cd booklore

# Add upstream remote to keep your fork in sync
git remote add upstream https://github.com/booklore-app/booklore.git
```

### 2. Keep Your Fork Updated

Before starting work on a new feature or fix:

```bash
# Fetch latest changes from upstream
git fetch upstream

# Merge upstream changes into your local main branch
git checkout main
git merge upstream/main

# Push updates to your fork
git push origin main
```

---

## 🧱 Local Development Setup

Booklore offers two development approaches: an all-in-one Docker stack for quick setup, or manual installation for more control.

### Option 1: Docker Development Stack (Recommended for Quick Start)

This option sets up everything with a single command:

```bash
docker compose -f dev.docker-compose.yml up
```

**What you get:**
- ✅ Frontend dev server at `http://localhost:4200/`
- ✅ Backend API at `http://localhost:8080/`
- ✅ MariaDB at `localhost:3366`
- ✅ Remote Java debugging at `localhost:5005`

**Note:** All ports are configurable via environment variables in `dev.docker-compose.yml`:
- `FRONTEND_PORT` (default: 4200)
- `BACKEND_PORT` (default: 8080)
- `DB_PORT` (default: 3366)
- `REMOTE_DEBUG_PORT` (default: 5005)

**Stopping the stack:**
```bash
docker compose -f dev.docker-compose.yml down
```

---

### Option 2: Manual Local Development

For more control over your development environment, you can run each component separately.

#### Prerequisites

Ensure you have the following installed:
- **Java 21+** ([Download](https://adoptium.net/))
- **Node.js 18+** and **npm** ([Download](https://nodejs.org/))
- **MariaDB 10.6+** ([Download](https://mariadb.org/download/))
- **Git** ([Download](https://git-scm.com/))

#### Frontend Setup

```bash
# Navigate to the frontend directory
cd booklore-ui

# Install dependencies
npm install

# Start the development server
ng serve

# Or use npm script
npm start
```

The frontend will be available at `http://localhost:4200/` with hot-reload enabled.

**Common Issues:**
- If you encounter dependency conflicts, try `npm install --legacy-peer-deps`
- Use `--force` only as a last resort

---

#### Backend Setup

##### Step 1: Configure Application Properties

Create a development configuration file at `booklore-api/src/main/resources/application-dev.yml`:

```yaml
app:
  # Path where books and comics are stored
  path-book: '/Users/yourname/booklore-data/books'
  
  # Path for thumbnails, metadata cache, and other config files
  path-config: '/Users/yourname/booklore-data/config'

spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://localhost:3306/booklore?createDatabaseIfNotExist=true
    username: root
    password: your_secure_password
```

**Important:**
- Replace `/Users/yourname/...` with actual paths on your system
- Create these directories if they don't exist
- Ensure proper read/write permissions

**Example paths:**
- **macOS/Linux**: `/Users/yourname/booklore-data/books`
- **Windows**: `C:\Users\yourname\booklore-data\books`

##### Step 2: Set Up the Database

Ensure MariaDB is running and create the database:

```bash
# Connect to MariaDB
mysql -u root -p

# Create database and user (optional)
CREATE DATABASE IF NOT EXISTS booklore;
CREATE USER 'booklore_user'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON booklore.* TO 'booklore_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

##### Step 3: Run the Backend

```bash
cd booklore-api
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The backend API will be available at `http://localhost:8080/`

**Verify it's running:**
```bash
curl http://localhost:8080/actuator/health
```

---

## 🧪 Testing

Always run tests before submitting a pull request to ensure your changes don't break existing functionality.

### Backend Tests

```bash
cd booklore-api

# Run all tests
./gradlew test

# Run tests with detailed output
./gradlew test --info

# Run a specific test class
./gradlew test --tests "com.booklore.api.service.BookServiceTest"

# Generate coverage report
./gradlew test jacocoTestReport
```

**Before creating a PR, always run:**
```bash
./gradlew test
```

---

## 🛠️ Contributing Guidelines

### 💡 Reporting Bugs

Found a bug? Help us fix it by providing detailed information:

1. **Search existing issues** to avoid duplicates
2. **Create a new issue** with the `bug` label
3. **Include the following:**
   - Clear, descriptive title (e.g., "Book import fails with PDF files over 100MB")
   - Steps to reproduce the issue
   - Expected behavior vs. actual behavior
   - Screenshots or error logs if applicable
   - Your environment (OS, browser, Docker version, etc.)

**Example Bug Report:**
```markdown
**Title:** Book metadata not updating after manual edit

**Description:**
When I manually edit a book's metadata through the UI and click Save, 
the changes appear to save but revert after page refresh.

**Steps to Reproduce:**
1. Navigate to any book detail page
2. Click "Edit Metadata"
3. Change the title from "Old Title" to "New Title"
4. Click "Save"
5. Refresh the page

**Expected:** Title should remain "New Title"
**Actual:** Title reverts to "Old Title"

**Environment:**
- Browser: Chrome 120
- OS: macOS 14.2
- Booklore Version: 1.2.0
```

---

### 🔃 Submitting Code Changes

#### Branch Naming Convention

Create descriptive branches that clearly indicate the purpose of your changes:

```bash
# For new features
git checkout -b feat/add-dark-mode-theme
git checkout -b feat/epub-reader-support

# For bug fixes
git checkout -b fix/book-import-validation
git checkout -b fix/memory-leak-in-scanner

# For documentation
git checkout -b docs/update-installation-guide

# For refactoring
git checkout -b refactor/improve-authentication-flow
```

#### Development Workflow

1. **Create a branch** from `develop` (not `main`)
2. **Make your changes** in small, logical commits
3. **Test thoroughly** - run both frontend and backend tests
4. **Update documentation** if your changes affect usage
5. **Run the linter** and fix any issues
6. **Commit with clear messages** following Conventional Commits
7. **Push to your fork**
8. **Open a pull request** targeting the `develop` branch

#### Pull Request Checklist

Before submitting, ensure:
- [ ] Code follows project conventions
- [ ] All tests pass (`./gradlew test` for backend)
- [ ] IntelliJ linter shows no errors
- [ ] Changes are documented (README, inline comments)
- [ ] PR description clearly explains what and why
- [ ] PR is linked to a related issue (if applicable)
- [ ] Branch is up-to-date with `develop`
- [ ] **For big features:** Create a documentation PR at [booklore-docs](https://github.com/booklore-app/booklore-docs) with styling similar to other documentation pages

---

## 🧼 Code Style & Conventions

- **Angular**: Follow the [official style guide](https://angular.io/guide/styleguide)
- **Java**: Use modern features (Java 21), clean structure
- **Linter**: Use IntelliJ IDEA's built-in linter for code formatting and style checks
- **UI**: Use SCSS and PrimeNG components consistently

---

## 📝 Commit Message Format

We follow [Conventional Commits](https://www.conventionalcommits.org/) for clear, standardized commit messages.

### Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements

### Examples

```bash
# Feature addition
feat(reader): add keyboard navigation for page turning

# Bug fix
fix(api): resolve memory leak in book scanning service

# Documentation
docs(readme): add troubleshooting section for Docker setup

# Multiple scopes
feat(api,ui): implement book collection management

# Breaking change
feat(auth)!: migrate to OAuth 2.1

BREAKING CHANGE: OAuth 2.0 is no longer supported
```

---

## 🙏 Code of Conduct

We're committed to providing a welcoming and inclusive environment for everyone.

**Our Standards:**
- ✅ Be respectful and considerate
- ✅ Welcome newcomers and help them learn
- ✅ Accept constructive criticism gracefully
- ✅ Focus on what's best for the community

**Unacceptable Behavior:**
- ❌ Harassment, trolling, or discrimination
- ❌ Personal attacks or insults
- ❌ Publishing others' private information
- ❌ Any conduct that would be inappropriate in a professional setting

**Enforcement:**
Instances of unacceptable behavior may result in temporary or permanent ban from the project.

---

## 💬 Community & Support

**Need help or want to discuss ideas?**

- 💬 **Discord**: [Join our server](https://discord.gg/Ee5hd458Uz)
- 🐛 **Issues**: [GitHub Issues](https://github.com/adityachandelgit/BookLore/issues)

---

## 📄 License

Booklore is open-source software licensed under the **GPL-3.0 License**.

By contributing, you agree that your contributions will be licensed under the same license. See the [`LICENSE`](./LICENSE) file for full details.

---

## 🎯 What to Work On?

Not sure where to start? Check out:

- Issues labeled [`good first issue`](https://github.com/adityachandelgit/BookLore/labels/good%20first%20issue)
- Issues labeled [`help wanted`](https://github.com/adityachandelgit/BookLore/labels/help%20wanted)
- Our [project roadmap](https://github.com/adityachandelgit/BookLore/projects)

---

## 🎉 Thank You!

Every contribution, no matter how small, makes Booklore better. Thank you for being part of our community!

**Happy Contributing! 📚✨**
