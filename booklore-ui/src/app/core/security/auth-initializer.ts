import {inject} from '@angular/core';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {AuthInitializationService} from './auth-initialization-service';
import {API_CONFIG} from '../config/api-config';
import {firstValueFrom} from 'rxjs';
import {filter, take, timeout} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {jwtDecode} from 'jwt-decode';

const OIDC_BYPASS_KEY = 'booklore-oidc-bypass';
const OIDC_ERROR_COUNT_KEY = 'booklore-oidc-error-count';
const OIDC_CONFIG_HASH_KEY = 'booklore-oidc-config-hash';
const MAX_OIDC_RETRIES = 3;
const OIDC_TIMEOUT_MS = 10000;
const SETTINGS_TIMEOUT_MS = 10000;

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

    if (!navigator.onLine) {
      console.warn('[Auth] App is offline, skipping auth initialization');
      authInitService.markAsInitialized();
      return;
    }

    // Pre-check internal token validity to prevent initial 401s
    const internalToken = authService.getInternalAccessToken();
    if (internalToken) {
      try {
        const decoded: any = jwtDecode(internalToken);
        const now = Date.now() / 1000;
        // Buffer of 10 seconds
        if (decoded.exp && (decoded.exp - now) < 10) {
          console.log('[Auth] Internal token expired or expiring soon, attempting refresh...');
          try {
            await firstValueFrom(authService.internalRefreshToken());
            console.log('[Auth] Internal token refreshed successfully');
          } catch (e) {
            console.warn('[Auth] Failed to refresh internal token during init, logging out', e);
            authService.logout();
          }
        }
      } catch (e) {
        console.error('[Auth] Failed to decode internal token, logging out', e);
        authService.logout();
      }
    }

    try {
      const publicSettings = await firstValueFrom(
        appSettingsService.publicAppSettings$.pipe(
          filter(x => !!x),
          take(1),
          timeout(SETTINGS_TIMEOUT_MS)
        )
      );

      // We explicitly check for null/undefined even though filter/take handles it safely above logic
      if (!publicSettings) {
         throw new Error('Public settings are null');
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
  const params = new URLSearchParams(window.location.search);
  const isCallback = params.has('code') && params.has('state');
  const hasToken = oauthService.hasValidAccessToken();

  if (isCallback) {
    // For callback, do not block app initialization.
    // OidcCallbackComponent will handle discovery loading and token exchange.
    authInitService.markAsInitialized();
    return;
  }

  if (hasToken) {
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
