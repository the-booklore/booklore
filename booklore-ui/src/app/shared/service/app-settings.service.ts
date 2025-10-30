import {inject, Injectable, Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, finalize, shareReplay, tap} from 'rxjs/operators';
import {API_CONFIG} from '../../core/config/api-config';
import {AppSettings, OidcProviderDetails} from '../model/app-settings.model';
import {AuthService} from './auth.service';

export interface PublicAppSettings {
  oidcEnabled: boolean;
  remoteAuthEnabled: boolean;
  oidcProviderDetails: OidcProviderDetails;
}

@Injectable({providedIn: 'root'})
export class AppSettingsService {
  private http = inject(HttpClient);
  private injector = inject(Injector);

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/settings`;
  private readonly publicApiUrl = `${API_CONFIG.BASE_URL}/api/v1/public-settings`;

  private loading$: Observable<AppSettings> | null = null;
  private appSettingsSubject = new BehaviorSubject<AppSettings | null>(null);

  appSettings$ = this.appSettingsSubject.asObservable().pipe(
    tap(state => {
      if (!state && !this.loading$) {
        this.loading$ = this.fetchAppSettings().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  private publicLoading$: Observable<PublicAppSettings> | null = null;
  private publicAppSettingsSubject = new BehaviorSubject<PublicAppSettings | null>(null);

  publicAppSettings$ = this.publicAppSettingsSubject.asObservable().pipe(
    tap(state => {
      if (!state && !this.publicLoading$) {
        this.publicLoading$ = this.fetchPublicSettings().pipe(
          shareReplay(1),
          finalize(() => (this.publicLoading$ = null))
        );
        this.publicLoading$.subscribe();
      }
    })
  );

  private fetchAppSettings(): Observable<AppSettings> {
    return this.http.get<AppSettings>(this.apiUrl).pipe(
      tap(settings => {
        this.appSettingsSubject.next(settings);
        this.syncPublicSettings(settings);
      }),
      catchError(err => {
        console.error('Error loading app settings:', err);
        this.appSettingsSubject.next(null);
        throw err;
      })
    );
  }

  private fetchPublicSettings(): Observable<PublicAppSettings> {
    return this.http.get<PublicAppSettings>(this.publicApiUrl).pipe(
      tap(settings => this.publicAppSettingsSubject.next(settings)),
      catchError(err => {
        console.error('Failed to fetch public settings', err);
        throw err;
      })
    );
  }

  private syncPublicSettings(appSettings: AppSettings): void {
    const updatedPublicSettings: PublicAppSettings = {
      oidcEnabled: appSettings.oidcEnabled,
      remoteAuthEnabled: appSettings.remoteAuthEnabled,
      oidcProviderDetails: appSettings.oidcProviderDetails
    };
    const current = this.publicAppSettingsSubject.value;

    if (
      !current ||
      current.oidcEnabled !== updatedPublicSettings.oidcEnabled ||
      current.remoteAuthEnabled !== updatedPublicSettings.remoteAuthEnabled ||
      JSON.stringify(current.oidcProviderDetails) !== JSON.stringify(updatedPublicSettings.oidcProviderDetails)
    ) {
      this.publicAppSettingsSubject.next(updatedPublicSettings);
    }
  }

  saveSettings(settings: { key: string; newValue: any }[]): Observable<void> {
    const payload = settings.map(setting => ({
      name: setting.key,
      value: setting.newValue
    }));

    return this.http.put<void>(this.apiUrl, payload).pipe(
      tap(() => {
        const current = this.appSettingsSubject.value;
        if (current) {
          settings.forEach(s => (current as any)[s.key] = s.newValue);
          this.appSettingsSubject.next({...current});
          this.syncPublicSettings(current);
        } else {
          this.loading$ = this.fetchAppSettings().pipe(
            shareReplay(1),
            finalize(() => (this.loading$ = null))
          );
          this.loading$.subscribe();
        }
      }),
      catchError(err => {
        console.error('Error saving settings:', err);
        return of();
      })
    );
  }

  toggleOidcEnabled(enabled: boolean): Observable<void> {
    const payload = [{name: 'OIDC_ENABLED', value: enabled}];
    return this.http.put<void>(this.apiUrl, payload).pipe(
      tap(() => {
        const current = this.appSettingsSubject.value;
        if (current) {
          current.oidcEnabled = enabled;
          this.appSettingsSubject.next({...current});
          this.syncPublicSettings(current);
        }
        if (!enabled) {
          setTimeout(() => {
            const authService = this.injector.get(AuthService);
            authService.clearOIDCTokens();
          });
        }
      }),
      catchError(err => {
        console.error('Error toggling OIDC:', err);
        return of();
      })
    );
  }
}
