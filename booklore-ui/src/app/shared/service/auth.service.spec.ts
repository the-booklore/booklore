import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {AuthService} from './auth.service';
import {OAuthService, OAuthStorage} from 'angular-oauth2-oidc';
import {Router} from '@angular/router';
import {PostLoginInitializerService} from '../../core/services/post-login-initializer.service';
import {RxStompService} from '../websocket/rx-stomp.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpClientMock: any;
  let oAuthServiceMock: any;
  let oAuthStorageMock: any;
  let routerMock: any;
  let postLoginInitializerMock: any;
  let rxStompServiceMock: any;
  let injectorMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn(),
      get: vi.fn()
    };
    oAuthServiceMock = {
      getIdToken: vi.fn().mockReturnValue('oidc-token')
    };
    oAuthStorageMock = {
      removeItem: vi.fn()
    };
    routerMock = {
      navigate: vi.fn()
    };
    postLoginInitializerMock = {
      initialize: vi.fn().mockReturnValue(of(void 0))
    };
    rxStompServiceMock = {
      updateConfig: vi.fn(),
      activate: vi.fn(),
      deactivate: vi.fn()
    };
    injectorMock = {
      get: vi.fn((token: any) => {
        if (token === RxStompService) return rxStompServiceMock;
        return undefined;
      })
    };

    vi.stubGlobal('localStorage', {
      getItem: vi.fn().mockReturnValue(null),
      setItem: vi.fn(),
      removeItem: vi.fn()
    });

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: OAuthService, useValue: oAuthServiceMock},
        {provide: OAuthStorage, useValue: oAuthStorageMock},
        {provide: Router, useValue: routerMock},
        {provide: PostLoginInitializerService, useValue: postLoginInitializerMock},
        {provide: RxStompService, useValue: rxStompServiceMock},
        {provide: 'Injector', useValue: injectorMock}
      ]
    });

    // Patch inject() for Injector
    (AuthService.prototype as any).injector = injectorMock;

    service = TestBed.inject(AuthService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should save and get internal tokens', () => {
    service.saveInternalTokens('access', 'refresh');
    expect(localStorage.setItem).toHaveBeenCalledWith('accessToken_Internal', 'access');
    expect(localStorage.setItem).toHaveBeenCalledWith('refreshToken_Internal', 'refresh');
    expect(service.tokenSubject.value).toBe('access');
  });

  it('should get internal access and refresh tokens', () => {
    (localStorage.getItem as any).mockImplementation((key: string) => key === 'accessToken_Internal' ? 'access' : 'refresh');
    expect(service.getInternalAccessToken()).toBe('access');
    expect(service.getInternalRefreshToken()).toBe('refresh');
  });

  it('should get OIDC access token', () => {
    expect(service.getOidcAccessToken()).toBe('oidc-token');
  });

  it('should call internalLogin and save tokens', () => {
    httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
    const spy = vi.spyOn(service, 'saveInternalTokens');
    service.internalLogin({username: 'u', password: 'p'}).subscribe(res => {
      expect(res.accessToken).toBe('a');
      expect(spy).toHaveBeenCalledWith('a', 'r');
    });
  });

  it('should call internalRefreshToken and save tokens', () => {
    (localStorage.getItem as any).mockReturnValue('refresh');
    httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r'}));
    const spy = vi.spyOn(service, 'saveInternalTokens');
    service.internalRefreshToken().subscribe(res => {
      expect(res.accessToken).toBe('a');
      expect(spy).toHaveBeenCalledWith('a', 'r');
    });
  });

  it('should call remoteLogin and save tokens', () => {
    httpClientMock.get.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
    const spy = vi.spyOn(service, 'saveInternalTokens');
    service.remoteLogin().subscribe(res => {
      expect(res.accessToken).toBe('a');
      expect(spy).toHaveBeenCalledWith('a', 'r');
    });
  });

  it('should clear OIDC tokens and navigate if no internal tokens', () => {
    (localStorage.getItem as any).mockReturnValue(null);
    service.clearOIDCTokens();
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('access_token');
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('refresh_token');
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('id_token');
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should not clear OIDC tokens if internal tokens exist', () => {
    (localStorage.getItem as any).mockReturnValue('access');
    service.clearOIDCTokens();
    expect(oAuthStorageMock.removeItem).not.toHaveBeenCalled();
    expect(routerMock.navigate).not.toHaveBeenCalled();
  });

  it('should logout and clear all tokens', () => {
    service.getRxStompService = vi.fn().mockReturnValue(rxStompServiceMock);
    routerMock.navigate.mockReturnValue(Promise.resolve(true));

    service.logout();

    expect(localStorage.removeItem).toHaveBeenCalledWith('accessToken_Internal');
    expect(localStorage.removeItem).toHaveBeenCalledWith('refreshToken_Internal');
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('access_token');
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('refresh_token');
    expect(oAuthStorageMock.removeItem).toHaveBeenCalledWith('id_token');
    expect(rxStompServiceMock.deactivate).toHaveBeenCalled();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should initialize websocket connection if token exists', () => {
    vi.spyOn(service, 'getOidcAccessToken').mockReturnValue('token');
    service.getRxStompService = vi.fn().mockReturnValue(rxStompServiceMock);
    service['postLoginInitialized'] = false;
    service.initializeWebSocketConnection();
    expect(rxStompServiceMock.updateConfig).toHaveBeenCalled();
    expect(rxStompServiceMock.activate).toHaveBeenCalled();
  });

  it('should not initialize websocket connection if no token', () => {
    vi.spyOn(service, 'getOidcAccessToken').mockReturnValue(null);
    vi.spyOn(service, 'getInternalAccessToken').mockReturnValue(null);
    service.getRxStompService = vi.fn().mockReturnValue(rxStompServiceMock);
    service.initializeWebSocketConnection();
    expect(rxStompServiceMock.updateConfig).not.toHaveBeenCalled();
    expect(rxStompServiceMock.activate).not.toHaveBeenCalled();
  });

  it('should call postLoginInitializer only once', () => {
    service['postLoginInitialized'] = false;
    service['handleSuccessfulAuth']();
    expect(postLoginInitializerMock.initialize).toHaveBeenCalled();
    service['handleSuccessfulAuth']();
    expect(postLoginInitializerMock.initialize).toHaveBeenCalledTimes(1);
  });

  it('should get RxStompService from injector', () => {
    service['rxStompService'] = undefined;
    expect(service.getRxStompService()).toBe(rxStompServiceMock);
    expect(service.getRxStompService()).toBe(rxStompServiceMock); // cached
  });
});

describe('AuthService - API Contract Tests', () => {
  let service: AuthService;
  let httpClientMock: any;
  let oAuthServiceMock: any;
  let oAuthStorageMock: any;
  let routerMock: any;
  let postLoginInitializerMock: any;
  let rxStompServiceMock: any;
  let injectorMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn(),
      get: vi.fn()
    };
    oAuthServiceMock = {getIdToken: vi.fn()};
    oAuthStorageMock = {removeItem: vi.fn()};
    routerMock = {navigate: vi.fn()};
    postLoginInitializerMock = {initialize: vi.fn().mockReturnValue(of(void 0))};
    rxStompServiceMock = {updateConfig: vi.fn(), activate: vi.fn(), deactivate: vi.fn()};
    injectorMock = {get: vi.fn((token: any) => rxStompServiceMock)};

    vi.stubGlobal('localStorage', {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn()
    });

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: OAuthService, useValue: oAuthServiceMock},
        {provide: OAuthStorage, useValue: oAuthStorageMock},
        {provide: Router, useValue: routerMock},
        {provide: PostLoginInitializerService, useValue: postLoginInitializerMock},
        {provide: RxStompService, useValue: rxStompServiceMock},
        {provide: 'Injector', useValue: injectorMock}
      ]
    });

    (AuthService.prototype as any).injector = injectorMock;
    service = TestBed.inject(AuthService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for internalLogin', () => {
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
      service.internalLogin({username: 'u', password: 'p'}).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/auth\/login$/),
        {username: 'u', password: 'p'}
      );
    });

    it('should call correct endpoint for internalRefreshToken', () => {
      (localStorage.getItem as any).mockReturnValue('refresh');
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r'}));
      service.internalRefreshToken().subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/auth\/refresh$/),
        {refreshToken: 'refresh'}
      );
    });

    it('should call correct endpoint for remoteLogin', () => {
      httpClientMock.get.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
      service.remoteLogin().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/auth\/remote$/)
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send correct structure for internalLogin', () => {
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
      const payload = {username: 'user', password: 'pass'};
      service.internalLogin(payload).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), payload);
    });

    it('should send correct structure for internalRefreshToken', () => {
      (localStorage.getItem as any).mockReturnValue('refresh');
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r'}));
      service.internalRefreshToken().subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(expect.any(String), {refreshToken: 'refresh'});
    });
  });

  describe('Response type contract', () => {
    it('should expect token object from internalLogin', () => {
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
      service.internalLogin({username: 'u', password: 'p'}).subscribe(res => {
        expect(res).toHaveProperty('accessToken');
        expect(res).toHaveProperty('refreshToken');
        expect(res).toHaveProperty('isDefaultPassword');
      });
    });

    it('should expect token object from remoteLogin', () => {
      httpClientMock.get.mockReturnValue(of({accessToken: 'a', refreshToken: 'r', isDefaultPassword: 'false'}));
      service.remoteLogin().subscribe(res => {
        expect(res).toHaveProperty('accessToken');
        expect(res).toHaveProperty('refreshToken');
        expect(res).toHaveProperty('isDefaultPassword');
      });
    });

    it('should expect token object from internalRefreshToken', () => {
      httpClientMock.post.mockReturnValue(of({accessToken: 'a', refreshToken: 'r'}));
      service.internalRefreshToken().subscribe(res => {
        expect(res).toHaveProperty('accessToken');
        expect(res).toHaveProperty('refreshToken');
      });
    });
  });
});

