import {inject, Injectable, Injector} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {BehaviorSubject, Observable, tap} from 'rxjs';
import {RxStompService} from '../websocket/rx-stomp.service';
import {API_CONFIG} from '../../core/config/api-config';
import {createRxStompConfig} from '../websocket/rx-stomp.config';
import {OAuthService, OAuthStorage} from 'angular-oauth2-oidc';
import {Router} from '@angular/router';
import {PostLoginInitializerService} from '../../core/services/post-login-initializer.service';

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/auth`;
  private rxStompService?: RxStompService;
  private postLoginInitialized = false;

  private http = inject(HttpClient);
  private injector = inject(Injector);
  private oAuthService = inject(OAuthService);
  private oAuthStorage = inject(OAuthStorage);
  private router = inject(Router);
  private postLoginInitializer = inject(PostLoginInitializerService);

  public tokenSubject = new BehaviorSubject<string | null>(this.getInternalAccessToken());
  public token$ = this.tokenSubject.asObservable();

  internalLogin(credentials: { username: string; password: string }): Observable<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }> {
    return this.http.post<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }>(`${this.apiUrl}/login`, credentials).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
          this.initializeWebSocketConnection();
          this.handleSuccessfulAuth();
        }
      })
    );
  }

  internalRefreshToken(): Observable<{ accessToken: string; refreshToken: string }> {
    const refreshToken = this.getInternalRefreshToken();
    return this.http.post<{ accessToken: string; refreshToken: string }>(`${this.apiUrl}/refresh`, {refreshToken}).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
        }
      })
    );
  }

  remoteLogin(): Observable<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }> {
    return this.http.get<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }>(`${this.apiUrl}/remote`).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
          this.initializeWebSocketConnection();
        }
      })
    );
  }

  exchangeOidcToken(): Observable<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }> {
    const oidcToken = this.getOidcAccessToken();
    if (!oidcToken) {
      throw new Error('No OIDC token available for exchange');
    }

    return this.http.post<{ accessToken: string; refreshToken: string, isDefaultPassword: string | boolean }>(
      `${this.apiUrl}/oidc/token`,
      { token: oidcToken },
      { headers: { 'X-Requested-With': 'XMLHttpRequest' } }
    ).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
          this.initializeWebSocketConnection();
          this.handleSuccessfulAuth();
        }
      })
    );
  }

  saveInternalTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('accessToken_Internal', accessToken);
    localStorage.setItem('refreshToken_Internal', refreshToken);
    this.tokenSubject.next(accessToken);
  }

  getInternalAccessToken(): string | null {
    return localStorage.getItem('accessToken_Internal');
  }

  getOidcAccessToken(): string | null {
    return this.oAuthService.getIdToken();
  }

  getInternalRefreshToken(): string | null {
    return localStorage.getItem('refreshToken_Internal');
  }

  clearOIDCTokens(): void {
    const hasInternalTokens = this.getInternalAccessToken() || this.getInternalRefreshToken();
    if (!hasInternalTokens) {
      this.oAuthStorage.removeItem("access_token");
      this.oAuthStorage.removeItem("refresh_token");
      this.oAuthStorage.removeItem("id_token");
      this.router.navigate(['/login']);
    }
  }

  logout(): void {
    localStorage.removeItem('accessToken_Internal');
    localStorage.removeItem('refreshToken_Internal');
    this.oAuthStorage.removeItem("access_token");
    this.oAuthStorage.removeItem("refresh_token");
    this.oAuthStorage.removeItem("id_token");
    this.tokenSubject.next(null);
    this.postLoginInitialized = false;
    this.getRxStompService().deactivate();
    // Force a full page reload to ensure OIDC configuration is refreshed from the backend
    window.location.href = '/login';
  }

  getRxStompService(): RxStompService {
    if (!this.rxStompService) {
      this.rxStompService = this.injector.get(RxStompService);
    }
    return this.rxStompService;
  }

  initializeWebSocketConnection(): void {
    // Only use internal token for WebSocket to avoid race conditions with OIDC user provisioning
    const token = this.getInternalAccessToken();
    if (!token) return;

    const stompService = this.getRxStompService();
    const config = createRxStompConfig(this);
    stompService.updateConfig(config);
    stompService.activate();

    if (!this.postLoginInitialized) {
      this.handleSuccessfulAuth();
    }
  }

  private handleSuccessfulAuth() {
    if (this.postLoginInitialized) return;
    this.postLoginInitialized = true;
    this.postLoginInitializer.initialize().subscribe({
      next: () => console.log('AuthService: Post-login initialization completed'),
      error: (err) => console.error('AuthService: Post-login initialization failed:', err)
    });
  }
}

export function websocketInitializer(authService: AuthService): () => void {
  return () => authService.initializeWebSocketConnection();
}
