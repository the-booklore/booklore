import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {MetadataMatchWeightsService} from './metadata-match-weights.service';
import {API_CONFIG} from '../../core/config/api-config';

describe('MetadataMatchWeightsService', () => {
  let service: MetadataMatchWeightsService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        MetadataMatchWeightsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(MetadataMatchWeightsService)
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call recalculateAll and return void', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    service.recalculateAll().subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        `${API_CONFIG.BASE_URL}/api/v1/books/metadata/recalculate-match-scores`,
        {}
      );
    });
  });

  it('should handle error from recalculateAll', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('fail')));
    service.recalculateAll().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('MetadataMatchWeightsService - API Contract Tests', () => {
  let service: MetadataMatchWeightsService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        MetadataMatchWeightsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(MetadataMatchWeightsService));
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for recalculateAll', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      service.recalculateAll().subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        `${API_CONFIG.BASE_URL}/api/v1/books/metadata/recalculate-match-scores`,
        {}
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send empty object as payload for recalculateAll', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      service.recalculateAll().subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.any(String),
        {}
      );
    });
  });

  describe('Response type contract', () => {
    it('should expect void from recalculateAll', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      service.recalculateAll().subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});

