# Ephemera Proxy Integration Plan

Booklore already enforces authentication and role-based permissions, so the safest way to expose Ephemera (an internal REST API at `http://10.129.20.50:8286`) is to terminate every request inside Booklore and selectively forward traffic to Ephemera. This document captures the current architecture observations and a concrete design for the proxy layer.

## Observations

### Frontend
- Angular standalone components with signals/`@if` blocks and PrimeNG widgets. Global shortcuts live in `AppTopBarComponent`, gated by `userService.userState$` permissions such as `canManipulateLibrary`.
- Navigation is route-driven for complex flows (e.g. `/bookdrop`, `/metadata-manager`) and the top bar delegates dialogs to `DialogLauncherService`.
- API access is centralized through `API_CONFIG.BASE_URL`; the Auth interceptor automatically attaches JWT tokens to `/api/` calls.
- High-leverage extension points: shared layout top bar, `app.routes`, and Prime dialogs.

### Backend
- Spring Boot with layered `controller -> service -> repository` structure; Gradle build. Security is handled via multiple `SecurityFilterChain`s in `SecurityConfig`, with `DualJwtAuthenticationFilter` handling OAuth2/JWT for any `/api/**` call.
- Existing proxy behavior (Kobo, KoReader) uses dedicated controllers/services that leverage `HttpClient` and path rewriting (`KoboServerProxy`) while preserving/rewriting headers as needed.
- Permissions are enforced either via guards on the frontend or via controller-level checks (e.g., `@PreAuthorize`) leveraging `BookLoreUser.UserPermissions`.
- Configuration is provided through `application.yaml` + `AppProperties` with structured subsections (`RemoteAuth`, `Swagger`, etc.).

## Requirements Recap
1. Keep Ephemera unreachable from the public internet; traffic must originate from Booklore and reuse Booklore authentication.
2. Ephemera exposes a REST API; Booklore users should invoke it via Booklore URLs (ideally `/api/v1/ephemera/**` and `/ephemera`).
3. No additional Ephemera auth—Booklore acts as the single enforcement point.
4. Provide a UI affordance (top-bar button) so that users with `canManipulateLibrary` (or admins) can reach the Ephemera tools quickly.

## Proposed Backend Architecture

### 1. Configuration
- Extend `AppProperties` with a nested `Ephemera` config section:
  ```yaml
  app:
    ephemera:
      base-url: http://10.129.20.50:8286
      connect-timeout-ms: 2000
      read-timeout-ms: 30000
      allowed-paths:
        - /api/**
        - /status/**
      allowed-methods: [GET, POST, PUT, DELETE, PATCH]
  ```
- Bind to `AppProperties.EphemeraProperties` and inject wherever needed.

### 2. Service Layer
- Create `EphemeraProxyService` modeled after `KoboServerProxy`, but leveraging Spring's `WebClient` (reactive, streaming-friendly) or `HttpClient`.
- Responsibilities:
  - Construct target URI by trimming `/api/v1/ephemera` prefix and joining with `base-url`.
  - Copy whitelisted headers (e.g., `Content-Type`, `Accept`, `X-Requested-With`), drop `Authorization` headers to prevent credential leakage, and inject contextual headers like `X-Booklore-User` or `X-Booklore-Request-Id` for auditing on the Ephemera side.
  - Support transparent streaming for file uploads/downloads (buffered for JSON, streaming for `multipart/form-data` and binary).
  - Enforce an allow-list check for both method and relative path to mitigate SSRF (reject anything outside `allowed-paths` with `403`).
  - Provide structured logging + metrics (duration, status, path) and optionally integrate with `MonitoringService`.

### 3. Controller Layer
- Add `EphemeraController`:
  ```java
  @RestController
  @RequestMapping("/api/v1/ephemera")
  @RequiredArgsConstructor
  public class EphemeraController {
      private final EphemeraProxyService proxyService;

      @PreAuthorize("@permissionEvaluator.canManipulateLibrary(#user)")
      @RequestMapping(value = "/**", method = {GET, POST, PUT, PATCH, DELETE})
      public ResponseEntity<?> proxyEphemera(HttpServletRequest request,
                                             HttpServletResponse response,
                                             Authentication authentication) {
          return proxyService.forward(request, response, (BookLoreUser) authentication.getPrincipal());
      }
  }
  ```
- The `forward` method should:
  - Extract body + query string.
  - Call Ephemera via `EphemeraProxyService`.
  - Copy relevant response headers/status/body back to the client.
  - Map Ephemera errors into Booklore `ApiError` responses while preserving status codes when safe.

### 4. Security
- No new `SecurityFilterChain` is required; `/api/v1/ephemera/**` will be covered by the existing JWT chain. However:
  - Add method-level security (`@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MANAGE_LIBRARY')")`) or implement a dedicated `PermissionEvaluator`.
  - Introduce rate limiting or throttling (e.g., via `Bucket4j`) if Ephemera is resource-constrained.
  - Consider adding a `Csrf` exemption since calls originate from authenticated SPA requests using JWTs (mirrors other JSON APIs).

### 5. Auditing & Observability
- Log every proxied call with: user id, method, Ephemera path, status, latency.
- Optionally emit events to `MonitoringService` for UI surfacing (e.g., show Ephemera outages in the notification popover).

### 6. Public Route for UI
- Serve `/ephemera` via Angular (see frontend plan below) which internally calls the proxy endpoints.
- For environments that prefer server-side rendering, provide `EphemeraViewController` (Spring MVC) that renders a static page embedding the SPA bundle, but this is optional because the Angular app already handles client-side routing.

## Frontend Integration

1. **Topbar Button (implemented)**
   - `AppTopBarComponent` now exposes `openEphemera()` which opens `/ephemera` in a new tab (same origin). Visible only to users with `canManipulateLibrary` or admins, aligning with Bookdrop permissions.
2. **Future Ephemera shell component**
   - Add `EphemeraShellComponent` under `features/ephemera/` (standalone) with routes `/ephemera` and maybe `/ephemera/:tool`.
   - Component can host an `<iframe>` hitting `/api/v1/ephemera/ui` or can orchestrate REST flows directly via Angular services that call `/api/v1/ephemera/**`.
   - Reuse existing `AuthInterceptor` so JWT headers attach automatically.

## End-to-End Flow
1. User clicks the Ephemera icon in the top bar; Angular opens `/ephemera` (SPA route).
2. Angular views use `EphemeraApiService` to call `/api/v1/ephemera/...`.
3. `DualJwtAuthenticationFilter` authenticates the request; `@PreAuthorize` validates permissions.
4. `EphemeraController` hands off to `EphemeraProxyService`, which sends the REST call to `http://10.129.20.50:8286/...`, adds Booklore context headers, and streams back the response.
5. Responses propagate back to the browser; errors are handled centrally (show toast, etc.).

## Hardening Checklist
- [ ] Input validation/allowlist for path and method.
- [ ] Timeout + retry policy (fail fast; never hang threads waiting for Ephemera).
- [ ] Circuit breaker (Resilience4j) to short-circuit when Ephemera is down.
- [ ] Optional response caching for idempotent GET endpoints if Ephemera is slow.
- [ ] Extensive integration tests hitting a mock Ephemera server to ensure headers and bodies are preserved.
- [ ] Infrastructure update: ensure the Booklore container/VPC can reach `10.129.20.50:8286` but that address remains private.

This plan keeps Ephemera isolated while letting Booklore users access it under Booklore’s URL space, leveraging existing authentication and authorization layers without exposing new attack surfaces.

