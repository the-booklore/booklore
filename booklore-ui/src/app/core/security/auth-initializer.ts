import {inject} from '@angular/core';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {AuthInitializationService} from './auth-initialization-service';
import {API_CONFIG} from '../config/api-config';
import {firstValueFrom} from 'rxjs';
import {filter, take, timeout} from 'rxjs/operators';
import {environment} from '../../../environments/environment';

const OIDC_BYPASS_KEY = 'booklore-oidc-bypass';
const OIDC_ERROR_COUNT_KEY = 'booklore-oidc-error-count';
const OIDC_CONFIG_HASH_KEY = 'booklore-oidc-config-hash';
const MAX_OIDC_RETRIES = 3;
const OIDC_TIMEOUT_MS = 10000;
const PUBLIC_SETTINGS_TIMEOUT_MS = 2000;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      setTimeout(() => reject(new Error(`Operation timed out after ${timeoutMs}ms`)), timeoutMs)
    )
  ]);
}

export function initializeAuthFactory() {
  return async () => {
    const oauthService = inject(OAuthService);
    const appSettingsService = inject(AppSettingsService);
    const authService = inject(AuthService);
    const authInitService = inject(AuthInitializationService);

    try {
      // Use firstValueFrom with timeout for faster resolution
      const publicSettings = await firstValueFrom(
        appSettingsService.publicAppSettings$.pipe(
          filter((settings): settings is PublicAppSettings => settings !== null),
          take(1),
          timeout(PUBLIC_SETTINGS_TIMEOUT_MS)
        )
      ).catch(() => null);

      if (!publicSettings) {
        console.warn('[Auth] Failed to load public settings, falling back to local auth');
        authInitService.markAsInitialized();
        return;
      }

      const forceLocalOnly = new URLSearchParams(window.location.search).get('localOnly') === 'true';
      const oidcBypassed = localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
      const errorCount = parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);

      // Fast path: Check if we should skip OIDC entirely
      if (forceLocalOnly || oidcBypassed || errorCount >= MAX_OIDC_RETRIES) {
        if (forceLocalOnly) {
          console.warn('[OIDC] Forced local-only login via ?localOnly=true');
        } else if (oidcBypassed) {
          // Silent fallback when manually bypassed
        } else {
          console.warn(`[OIDC] OIDC automatically bypassed due to ${errorCount} consecutive errors`);
        }
        authInitService.markAsInitialized();
        return;
      }

      if (publicSettings.oidcEnabled && publicSettings.oidcProviderDetails) {
        await handleOidcFlow(oauthService, authInitService, publicSettings);
        return;
      }

      if (publicSettings.remoteAuthEnabled) {
        await handleRemoteAuth(authService, authInitService);
        return;
      }

      authInitService.markAsInitialized();
    } catch (err) {
      console.error('[Auth] Initialization error:', err);
      authInitService.markAsInitialized();
    }
  };
}

async function handleOidcFlow(
  oauthService: OAuthService,
  authInitService: AuthInitializationService,
  publicSettings: PublicAppSettings
): Promise<void> {
  const details = publicSettings.oidcProviderDetails!;

  // Check if config changed and reset bypass
  const newConfigHash = `${details.issuerUri}|${details.clientId}|${details.providerName}`;
  const storedHash = localStorage.getItem(OIDC_CONFIG_HASH_KEY);
  if (storedHash !== newConfigHash) {
    resetOidcBypass();
    localStorage.setItem(OIDC_CONFIG_HASH_KEY, newConfigHash);
  }

  const bypassActive = localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
  const currentErrorCount = parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);

  if (bypassActive || currentErrorCount >= MAX_OIDC_RETRIES) {
    authInitService.markAsInitialized();
    return;
  }

  // Configure OAuth synchronously
  oauthService.configure({
    issuer: details.issuerUri,
    clientId: details.clientId,
    scope: 'openid profile email offline_access',
    redirectUri: window.location.origin + '/oauth2-callback',
    responseType: 'code',
    showDebugInformation: !environment.production,
    requireHttps: environment.production,
    strictDiscoveryDocumentValidation: false
  });

  const discoveryDocumentUrl = API_CONFIG.AUTH.OIDC_DISCOVERY;

  // Define the setup task (Discovery + Login)
  const runOidcSetup = async (isBackground: boolean) => {
    try {
      await withTimeout(
        oauthService.loadDiscoveryDocument(discoveryDocumentUrl)
          .then(() => oauthService.tryLogin()),
        OIDC_TIMEOUT_MS
      );

      localStorage.removeItem(OIDC_ERROR_COUNT_KEY);

      if (oauthService.hasValidAccessToken()) {
        oauthService.setupAutomaticSilentRefresh();
        if (!isBackground) authInitService.markAsInitialized();
      } else {
        if (!isBackground) authInitService.markAsInitialized();
      }
    } catch (err: any) {
      const newErrorCount = currentErrorCount + 1;
      localStorage.setItem(OIDC_ERROR_COUNT_KEY, newErrorCount.toString());

      const isTimeout = err.message?.includes('timed out');
      const errorType = isTimeout ? 'timeout' : 'network/configuration';

      console.error(
        `[OIDC] Initialization failed (${errorType}, attempt ${newErrorCount}/${MAX_OIDC_RETRIES}):`,
        err.message || err
      );

      if (newErrorCount >= MAX_OIDC_RETRIES) {
        console.warn(`[OIDC] Maximum retry attempts exceeded. OIDC will be automatically bypassed.`);
        localStorage.setItem(OIDC_BYPASS_KEY, 'true');
      }

      if (!isBackground) authInitService.markAsInitialized();
    }
  };

  // We check for 'code' and 'state' in the URL to identify a callback
  const isCallback = window.location.search.includes('code=') && window.location.search.includes('state=');
  const hasToken = oauthService.hasValidAccessToken();

  if (hasToken && !isCallback) {
    authInitService.markAsInitialized();
    runOidcSetup(true);
  } else {
    await runOidcSetup(false);
  }
}

async function handleRemoteAuth(
  authService: AuthService,
  authInitService: AuthInitializationService
): Promise<void> {
  try {
    await firstValueFrom(authService.remoteLogin());
  } catch (err) {
    console.error('[Remote Login] failed:', err);
  }
  authInitService.markAsInitialized();
}

export function resetOidcBypass(): void {
  localStorage.removeItem(OIDC_BYPASS_KEY);
  localStorage.removeItem(OIDC_ERROR_COUNT_KEY);
}

export function isOidcBypassed(): boolean {
  return localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
}

export function getOidcErrorCount(): number {
  return parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);
}
