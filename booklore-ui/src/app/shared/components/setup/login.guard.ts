import { inject, Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, map, catchError, of, switchMap } from 'rxjs';
import { API_CONFIG } from '../../../core/config/api-config';
import { AppSettingsService } from '../../service/app-settings.service';
import { OAuthService } from 'angular-oauth2-oidc';
import { take } from 'rxjs/operators';

const OIDC_BYPASS_KEY = 'booklore-oidc-bypass';
const OIDC_ERROR_COUNT_KEY = 'booklore-oidc-error-count';
const MAX_OIDC_RETRIES = 3;

@Injectable({
  providedIn: 'root',
})
export class LoginGuard implements CanActivate {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/setup`;
  private http = inject(HttpClient);
  private router = inject(Router);
  private appSettingsService = inject(AppSettingsService);
  private oAuthService = inject(OAuthService);

  canActivate(): Observable<boolean> {
    return this.http.get<{ data: boolean }>(`${this.url}/status`).pipe(
      switchMap(res => {
        if (!res.data) {
          this.router.navigate(['/setup']);
          return of(false);
        }

        // Check if OIDC is enabled and should redirect automatically
        return this.appSettingsService.publicAppSettings$.pipe(
          take(1),
          map(publicSettings => {
            if (!publicSettings) {
              return true; // Allow access to login if settings unavailable
            }

            // Check for bypass conditions
            const forceLocalOnly = new URLSearchParams(window.location.search).get('localOnly') === 'true';
            const oidcBypassed = localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
            const errorCount = parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);

            // If OIDC is enabled and not bypassed, redirect to OIDC provider
            if (publicSettings.oidcEnabled &&
                publicSettings.oidcProviderDetails &&
                !forceLocalOnly &&
                !oidcBypassed &&
                errorCount < MAX_OIDC_RETRIES) {

              // Check if user already has a valid token (might be returning from OIDC)
              if (this.oAuthService.hasValidAccessToken()) {
                this.router.navigate(['/dashboard']);
                return false;
              }

              // User needs to login - allow showing login page
              // The login page will handle the OIDC redirect button
              return true;
            }

            return true; // Allow access to local login page
          })
        );
      }),
      catchError(() => {
        this.router.navigate(['/setup']);
        return of(false);
      })
    );
  }
}
