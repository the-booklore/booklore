import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {HardcoverSyncSettingsService, HardcoverSyncSettings} from './hardcover-sync-settings.service';

describe('HardcoverSyncSettingsService', () => {
  let service: HardcoverSyncSettingsService;
  let httpClientMock: any;

  const mockSettings: HardcoverSyncSettings = {
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
        HardcoverSyncSettingsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(HardcoverSyncSettingsService));
  });

  it('should get Hardcover sync settings', () => {
    httpClientMock.get.mockReturnValue(of(mockSettings));
    service.getSettings().subscribe(settings => {
      expect(settings).toEqual(mockSettings);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/api/v1/hardcover-sync-settings'));
    });
  });

  it('should update Hardcover sync settings', () => {
    httpClientMock.put.mockReturnValue(of(mockSettings));
    service.updateSettings(mockSettings).subscribe(settings => {
      expect(settings).toEqual(mockSettings);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/api/v1/hardcover-sync-settings'), mockSettings);
    });
  });

  it('should handle getSettings error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getSettings().subscribe({
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

describe('HardcoverSyncSettingsService - API Contract Tests', () => {
  let service: HardcoverSyncSettingsService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        HardcoverSyncSettingsService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(HardcoverSyncSettingsService));
  });

  it('should call correct endpoint for getSettings', () => {
    httpClientMock.get.mockReturnValue(of({}));
    service.getSettings().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/hardcover-sync-settings$/));
  });

  it('should call correct endpoint for updateSettings', () => {
    httpClientMock.put.mockReturnValue(of({}));
    const settings: HardcoverSyncSettings = {
      hardcoverApiKey: 'key',
      hardcoverSyncEnabled: false
    };
    service.updateSettings(settings).subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/hardcover-sync-settings$/), settings);
  });
});
