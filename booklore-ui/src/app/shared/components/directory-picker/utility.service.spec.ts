import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {UtilityService} from './utility.service';

describe('UtilityService', () => {
  let service: UtilityService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        UtilityService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(UtilityService)
    );
  });

  it('should get folders for a given path', () => {
    httpClientMock.get.mockReturnValue(of(['folder1', 'folder2']));
    service.getFolders('/some/path').subscribe(folders => {
      expect(folders).toEqual(['folder1', 'folder2']);
      expect(httpClientMock.get).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/path'),
        {params: expect.anything()}
      );
    });
  });

  it('should handle error when getting folders', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getFolders('/bad/path').subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('UtilityService - API Contract Tests', () => {
  let service: UtilityService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        UtilityService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(
      injector,
      () => TestBed.inject(UtilityService)
    );
  });

  it('should call correct endpoint and params for getFolders', () => {
    httpClientMock.get.mockReturnValue(of(['a', 'b']));
    service.getFolders('/root').subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/path$/),
      {params: expect.objectContaining({get: expect.any(Function)})}
    );
  });

  it('should expect string[] from getFolders', () => {
    httpClientMock.get.mockReturnValue(of(['dirA', 'dirB']));
    service.getFolders('/dir').subscribe(result => {
      expect(Array.isArray(result)).toBe(true);
      expect(typeof result[0]).toBe('string');
    });
  });

  it('should send correct query param for path', () => {
    httpClientMock.get.mockImplementation((url: string, opts: any) => {
      expect(opts.params.get('path')).toBe('/my/path');
      return of([]);
    });
    service.getFolders('/my/path').subscribe();
  });
});

