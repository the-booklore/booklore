import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';

import {NewPdfReaderService} from './new-pdf-reader.service';
import {AuthService} from '../../../shared/service/auth.service';
import {API_CONFIG} from '../../../core/config/api-config';

describe('NewPdfReaderService', () => {
  let service: NewPdfReaderService;
  let authServiceMock: any;
  let httpClientMock: any;

  beforeEach(() => {
    authServiceMock = {
      getOidcAccessToken: vi.fn(),
      getInternalAccessToken: vi.fn()
    };

    httpClientMock = {
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        NewPdfReaderService,
        {provide: AuthService, useValue: authServiceMock},
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(NewPdfReaderService)
    );
  });

  it('should append token from OIDC access token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('oidc-token');
    authServiceMock.getInternalAccessToken.mockReturnValue(null);

    const url = 'http://test.com/resource';
    const result = (service as any).appendToken(url);

    expect(result).toContain('token=oidc-token');
  });

  it('should not append token if both tokens are null', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue(null);
    authServiceMock.getInternalAccessToken.mockReturnValue(null);

    const url = 'http://test.com/resource';
    const result = (service as any).appendToken(url);

    expect(result).toBe(url);
  });

  it('should call http.get with correct URL', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('token123');

    service.getAvailablePages(42);

    expect(httpClientMock.get).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/pdf/42/pages?token=token123`
    );
  });
});
