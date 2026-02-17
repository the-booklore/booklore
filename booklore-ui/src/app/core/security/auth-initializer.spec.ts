import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {initializeAuthFactory} from './auth-initializer';
import {AuthInitializationService} from './auth-initialization-service';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {BehaviorSubject} from 'rxjs';

describe('initializeAuthFactory', () => {
  let authInitService: AuthInitializationService;
  let publicSettingsSubject: BehaviorSubject<PublicAppSettings | null>;

  beforeEach(() => {
    publicSettingsSubject = new BehaviorSubject<PublicAppSettings | null>(null);

    TestBed.configureTestingModule({
      providers: [
        {provide: OAuthService, useValue: {configure: vi.fn(), loadDiscoveryDocumentAndTryLogin: vi.fn()}},
        {provide: AuthService, useValue: {tokenSubject: {next: vi.fn()}}},
        {provide: AppSettingsService, useValue: {publicAppSettings$: publicSettingsSubject.asObservable()}},
        AuthInitializationService,
      ]
    });

    authInitService = TestBed.inject(AuthInitializationService);
  });

  it('should proceed with auth initialization when navigator.onLine is false', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: false, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({oidcEnabled: false, remoteAuthEnabled: false, oidcProviderDetails: null!});

    await initPromise;

    expect(markSpy).toHaveBeenCalled();

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});
  });

  it('should initialize normally when navigator.onLine is true', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    publicSettingsSubject.next({oidcEnabled: false, remoteAuthEnabled: false, oidcProviderDetails: null!});

    await initPromise;

    expect(markSpy).toHaveBeenCalled();
  });
});
