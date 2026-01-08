import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {CbxReaderService} from './cbx-reader.service';
import {AuthService} from '../../../shared/service/auth.service';

describe('CbxReaderService', () => {
  let service: CbxReaderService;
  let httpClientMock: any;
  let authServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };

    authServiceMock = {
      getOidcAccessToken: vi.fn(),
      getInternalAccessToken: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        CbxReaderService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(CbxReaderService)
    );
  });

  it('should get available pages with token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('abc123');
    authServiceMock.getInternalAccessToken.mockReturnValue(null);
    httpClientMock.get.mockReturnValue(of([1, 2, 3]));
    service.getAvailablePages(42).subscribe(pages => {
      expect(pages).toEqual([1, 2, 3]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('token=abc123'));
    });
  });

  it('should get available pages without token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue(null);
    authServiceMock.getInternalAccessToken.mockReturnValue(null);
    httpClientMock.get.mockReturnValue(of([4, 5]));
    service.getAvailablePages(99).subscribe(pages => {
      expect(pages).toEqual([4, 5]);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.not.stringContaining('token='));
    });
  });

  it('should handle error when getting available pages', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('tok');
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getAvailablePages(1).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should return correct page image url with oidc token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue('tok');
    authServiceMock.getInternalAccessToken.mockReturnValue(null);
    const url = service.getPageImageUrl(5, 7);
    expect(url).toContain('/api/v1/media/book/5/cbx/pages/7');
    expect(url).toContain('token=tok');
  });

  it('should return correct page image url with internal token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue(null);
    authServiceMock.getInternalAccessToken.mockReturnValue('inttok');
    const url = service.getPageImageUrl(8, 2);
    expect(url).toContain('/api/v1/media/book/8/cbx/pages/2');
    expect(url).toContain('token=inttok');
  });

  it('should return correct page image url without token', () => {
    authServiceMock.getOidcAccessToken.mockReturnValue(null);
    authServiceMock.getInternalAccessToken.mockReturnValue(null);
    const url = service.getPageImageUrl(3, 1);
    expect(url).toContain('/api/v1/media/book/3/cbx/pages/1');
    expect(url).not.toContain('token=');
  });
});

