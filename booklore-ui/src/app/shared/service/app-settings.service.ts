import {inject, Injectable, Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of, ReplaySubject} from 'rxjs';
import {catchError, finalize, map, shareReplay, switchMap, tap, timeout} from 'rxjs/operators';
import {API_CONFIG} from '../../core/config/api-config';
import {AppSettings, OidcProviderDetails} from '../model/app-settings.model';
import {AuthService} from './auth.service';

export interface PublicAppSettings {
  oidcEnabled: boolean;
  remoteAuthEnabled: boolean;
  oidcProviderDetails: OidcProviderDetails | null;
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

  // Use ReplaySubject to cache the last emitted value and emit immediately to new subscribers
  private publicAppSettingsSubject = new ReplaySubject<PublicAppSettings | null>(1);
  private publicSettingsLoaded = false;

  // Eagerly fetch public settings - no lazy loading for this critical path
  publicAppSettings$ = this.publicAppSettingsSubject.asObservable();

  constructor() {
    // Immediately start fetching public settings on service instantiation
    this.loadPublicSettings();
  }

  private loadPublicSettings(): void {
    if (this.publicSettingsLoaded) return;
    this.publicSettingsLoaded = true;

    this.http.get<PublicAppSettings>(this.publicApiUrl).pipe(
      timeout(5000),
      catchError(err => {
        console.error('Failed to fetch public settings', err);
        // Emit safe default to unblock waiting subscribers
        return of({
          oidcEnabled: false,
          remoteAuthEnabled: false,
          oidcProviderDetails: null
        } as PublicAppSettings);
      })
    ).subscribe(settings => {
      this.publicAppSettingsSubject.next(settings);
    });
  }

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

  private syncPublicSettings(appSettings: AppSettings): void {
    const updatedPublicSettings: PublicAppSettings = {
      oidcEnabled: appSettings.oidcEnabled,
      remoteAuthEnabled: appSettings.remoteAuthEnabled,
      oidcProviderDetails: appSettings.oidcProviderDetails
    };
    // Always update since ReplaySubject doesn't have .value accessor
    this.publicAppSettingsSubject.next(updatedPublicSettings);
  }

  saveSettings(settings: { key: string; newValue: unknown }[]): Observable<void> {
    const payload = settings.map(setting => ({
      name: setting.key,
      value: setting.newValue
    }));

    return this.http.put<void>(this.apiUrl, payload).pipe(
      switchMap(() => this.fetchAppSettings()),
      map(() => void 0),
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
          const authService = this.injector.get(AuthService);
          setTimeout(() => {
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
