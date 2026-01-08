import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {KoboService, KoboSyncSettings} from './kobo.service';

describe('KoboService', () => {
  let service: KoboService;
  let httpClientMock: any;

  const mockSettings: KoboSyncSettings = {
    token: 'abc123',
    syncEnabled: true,
    progressMarkAsReadingThreshold: 10,
    progressMarkAsFinishedThreshold: 90,
    autoAddToShelf: false,
    hardcoverApiKey: 'hardcover-key',
    hardcoverSyncEnabled: true
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        KoboService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(KoboService));
  });

  it('should get user Kobo settings', () => {
    httpClientMock.get.mockReturnValue(of(mockSettings));
    service.getUser().subscribe(settings => {
      expect(settings).toEqual(mockSettings);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/api/v1/kobo-settings'));
    });
  });

  it('should create or update token', () => {
    httpClientMock.put.mockReturnValue(of(mockSettings));
    service.createOrUpdateToken().subscribe(settings => {
      expect(settings).toEqual(mockSettings);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/api/v1/kobo-settings/token'), null);
    });
  });

  it('should update settings', () => {
    httpClientMock.put.mockReturnValue(of(mockSettings));
    service.updateSettings(mockSettings).subscribe(settings => {
      expect(settings).toEqual(mockSettings);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/api/v1/kobo-settings'), mockSettings);
    });
  });

  it('should handle getUser error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getUser().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle createOrUpdateToken error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.createOrUpdateToken().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle updateSettings error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.updateSettings(mockSettings).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('KoboService - API Contract Tests', () => {
  let service: KoboService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        KoboService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(KoboService));
  });

  it('should validate all required KoboSyncSettings fields exist', () => {
    const requiredFields: (keyof KoboSyncSettings)[] = [
      'token', 'syncEnabled', 'autoAddToShelf'
    ];

    const mockResponse: KoboSyncSettings = {
      token: 'abc',
      syncEnabled: true,
      autoAddToShelf: false,
      progressMarkAsReadingThreshold: 10,
      progressMarkAsFinishedThreshold: 90,
      hardcoverApiKey: 'hardcover-key',
      hardcoverSyncEnabled: true
    };

    httpClientMock.get.mockReturnValue(of(mockResponse));

    service.getUser().subscribe(settings => {
      requiredFields.forEach(field => {
        expect(settings).toHaveProperty(field);
        expect(settings[field]).toBeDefined();
      });
    });
  });

  it('should fail if API returns KoboSyncSettings without required token field', () => {
    const invalidResponse = {
      syncEnabled: true,
      autoAddToShelf: false
    };

    httpClientMock.get.mockReturnValue(of(invalidResponse));

    service.getUser().subscribe(settings => {
      expect(settings).not.toHaveProperty('token');
    });
  });

  it('should fail if API returns KoboSyncSettings without required syncEnabled field', () => {
    const invalidResponse = {
      token: 'abc',
      autoAddToShelf: false
    };

    httpClientMock.get.mockReturnValue(of(invalidResponse));

    service.getUser().subscribe(settings => {
      expect(settings).not.toHaveProperty('syncEnabled');
    });
  });

  it('should fail if API returns KoboSyncSettings without required autoAddToShelf field', () => {
    const invalidResponse = {
      token: 'abc',
      syncEnabled: true
    };

    httpClientMock.get.mockReturnValue(of(invalidResponse));

    service.getUser().subscribe(settings => {
      expect(settings).not.toHaveProperty('autoAddToShelf');
    });
  });

  it('should call correct endpoint for getUser', () => {
    httpClientMock.get.mockReturnValue(of({}));
    service.getUser().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/kobo-settings$/));
  });

  it('should call correct endpoint for createOrUpdateToken', () => {
    httpClientMock.put.mockReturnValue(of({}));
    service.createOrUpdateToken().subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/kobo-settings\/token$/), null);
  });

  it('should call correct endpoint for updateSettings', () => {
    httpClientMock.put.mockReturnValue(of({}));
    const settings: KoboSyncSettings = {
      token: 'abc',
      syncEnabled: true,
      autoAddToShelf: false
    };
    service.updateSettings(settings).subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/kobo-settings$/), settings);
  });
});


