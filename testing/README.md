# BookLore Testing Infrastructure

This directory contains integration and end-to-end testing scripts for BookLore.

## Test Scripts

### Docker Integration Tests (`docker-integration-test.sh`)

Tests the Docker container health and basic functionality.

```bash
# Basic usage
./testing/docker-integration-test.sh

# With custom image
./testing/docker-integration-test.sh --image myimage:tag

# With custom port
./testing/docker-integration-test.sh --port 8080

# With custom timeout
./testing/docker-integration-test.sh --timeout 180
```

### Webpage Accessibility Tests (`test-webpages.sh`)

Tests that all expected application routes return valid HTTP responses.

```bash
# Test against running local instance
./testing/test-webpages.sh

# Test against custom URL
./testing/test-webpages.sh --url http://myserver:6060

# With custom timeout
./testing/test-webpages.sh --timeout 30
```

### Swagger/OpenAPI Endpoint Tests (`swagger_endpoint_test.py`)

Tests all API endpoints documented in the OpenAPI specification. The script fetches the API spec from the running server and attempts to call each endpoint with appropriate test data.

**Prerequisites:**
```bash
pip install -r testing/requirements.txt
```

**Usage:**
```bash
# Basic usage (tests public endpoints only)
python testing/swagger_endpoint_test.py

# With custom URL
python testing/swagger_endpoint_test.py --base-url http://localhost:6060

# With authentication (tests protected endpoints)
python testing/swagger_endpoint_test.py --enable-auth --username admin --password admin

# Verbose output
python testing/swagger_endpoint_test.py --verbose

# Include destructive endpoints (DELETE, etc.)
python testing/swagger_endpoint_test.py --include-destructive
```

**Environment Variables:**
- `BOOKLORE_URL` - Base URL of the BookLore server
- `BOOKLORE_USER` - Username for authentication
- `BOOKLORE_PASSWORD` - Password for authentication

**Note:** Swagger must be enabled on the server (`SWAGGER_ENABLED=true`) for these tests to work.

## Test Categories

### 1. Backend Unit Tests (Java)

Located in `booklore-api/src/test/java/`

Run with:
```bash
cd booklore-api
./gradlew test
```

### 2. Frontend Unit Tests (Angular/Jasmine)

Located in `booklore-ui/src/app/**/*.spec.ts`

Run with:
```bash
cd booklore-ui
npm run test
```

Or headless:
```bash
npm run test -- --no-watch --no-progress --browsers=ChromeHeadless
```

### 3. Docker Integration Tests

Run the Docker integration test script to verify container health:

```bash
chmod +x testing/docker-integration-test.sh
./testing/docker-integration-test.sh
```

### 4. API Endpoint Tests (Swagger/OpenAPI)

Run the Swagger endpoint tests to validate all API endpoints:

```bash
pip install -r testing/requirements.txt
python testing/swagger_endpoint_test.py --verbose
```

Or with authentication for protected endpoints:

```bash
python testing/swagger_endpoint_test.py --enable-auth --username admin --password admin
```

## CI/CD Integration

Tests are automatically run in GitHub Actions on:
- Pull requests to `main` and `develop` branches
- Manual workflow dispatch

See `.github/workflows/build.yml` for the full CI configuration.

## Adding New Tests

### Backend (Java)
1. Create test files in `booklore-api/src/test/java/`
2. Use JUnit 5 with `@Test` annotations
3. Use Mockito for mocking dependencies
4. Follow the existing test patterns in the codebase

### Frontend (Angular)
1. Create `.spec.ts` files alongside the component/service being tested
2. Use Jasmine for assertions
3. Use Angular Testing utilities (`TestBed`, etc.)
4. Mock HTTP calls with `HttpClientTestingModule`

### Integration Tests

1. Add new test endpoints to `test-webpages.sh`
2. Add new container tests to `docker-integration-test.sh`
3. Add API endpoint patterns to `swagger_endpoint_test.py` (in the EndpointClassifier class)

### API Tests (Swagger)

1. Update `swagger_endpoint_test.py` with new endpoint classifications
2. Add special request body handling for new endpoints in `SPECIAL_ENDPOINTS`
3. Add new BookLore-specific parameter defaults in `BOOKLORE_PARAMS`

## Code Coverage

### Backend
```bash
cd booklore-api
./gradlew test jacocoTestReport
# Report available at build/reports/jacoco/test/html/index.html
```

### Frontend
```bash
cd booklore-ui
npm run test -- --code-coverage
# Report available at coverage/booklore/index.html
```
