import {inject, Injectable, Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, tap} from 'rxjs';
import {RxStompService} from '../websocket/rx-stomp.service';
import {API_CONFIG} from '../../core/config/api-config';
import {createRxStompConfig} from '../websocket/rx-stomp.config';
import {OAuthService, OAuthStorage} from 'angular-oauth2-oidc';
import {Router} from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/auth`;
  private rxStompService?: RxStompService;

  private http = inject(HttpClient);
  private injector = inject(Injector);
  private oAuthService = inject(OAuthService);
  private oAuthStorage = inject(OAuthStorage);
  private router = inject(Router);

  public tokenSubject = new BehaviorSubject<string | null>(this.getOidcAccessToken() || this.getInternalAccessToken());
  public token$ = this.tokenSubject.asObservable();


  internalLogin(credentials: { username: string; password: string }): Observable<{ accessToken: string; refreshToken: string, isDefaultPassword: string }> {
    return this.http.post<{ accessToken: string; refreshToken: string, isDefaultPassword: string }>(`${this.apiUrl}/login`, credentials).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
          this.initializeWebSocketConnection();
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

  remoteLogin(): Observable<{ accessToken: string; refreshToken: string, isDefaultPassword: string }> {
    return this.http.get<{ accessToken: string; refreshToken: string, isDefaultPassword: string }>(`${this.apiUrl}/remote`).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveInternalTokens(response.accessToken, response.refreshToken);
          this.initializeWebSocketConnection();
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
    this.getRxStompService().deactivate();
    this.router.navigate(['/login']);
  }

  getRxStompService(): RxStompService {
    if (!this.rxStompService) {
      this.rxStompService = this.injector.get(RxStompService);
    }
    return this.rxStompService;
  }

  initializeWebSocketConnection(): void {
    const token = this.getOidcAccessToken() || this.getInternalAccessToken();
    if (!token) return;

    const stompService = this.getRxStompService();
    const config = createRxStompConfig(this);
    stompService.updateConfig(config);
    stompService.activate();
  }
}

export function websocketInitializer(authService: AuthService): () => void {
  return () => authService.initializeWebSocketConnection();
}
