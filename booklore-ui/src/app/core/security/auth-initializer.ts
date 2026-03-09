import {inject} from '@angular/core';
import {AuthService, websocketInitializer} from '../../shared/service/auth.service';
import {AppSettingsService} from '../../shared/service/app-settings.service';
import {AuthInitializationService} from './auth-initialization-service';

const SETTINGS_TIMEOUT_MS = 10000;

export function initializeAuthFactory() {
  return () => {
    const appSettingsService = inject(AppSettingsService);
    const authService = inject(AuthService);
    const authInitService = inject(AuthInitializationService);

    return new Promise<void>((resolve) => {

      const settingsTimeout = setTimeout(() => {
        console.warn('[Auth] Public settings fetch timed out, falling back to local auth');
        sub.unsubscribe();
        authInitService.markAsInitialized();
        resolve();
      }, SETTINGS_TIMEOUT_MS);

      const sub = appSettingsService.publicAppSettings$.subscribe(publicSettings => {
        if (publicSettings) {
          clearTimeout(settingsTimeout);

          if (publicSettings.remoteAuthEnabled) {
            authService.remoteLogin().subscribe({
              next: () => {
                authInitService.markAsInitialized();
                resolve();
              },
              error: err => {
                console.error('[Remote Login] failed:', err);
                authInitService.markAsInitialized();
                resolve();
              }
            });
          } else {
            if (authService.getInternalAccessToken()) {
              websocketInitializer(authService)();
            }
            authInitService.markAsInitialized();
            resolve();
          }
          sub.unsubscribe();
        }
      });
    });
  };
}
