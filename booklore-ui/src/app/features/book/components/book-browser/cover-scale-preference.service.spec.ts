import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {take} from 'rxjs/operators';

describe('CoverScalePreferenceService', () => {
  let messageServiceMock: any;
  let localStorageServiceMock: any;

  beforeEach(() => {
    messageServiceMock = {add: vi.fn()};
    localStorageServiceMock = {
      set: vi.fn(),
      get: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: MessageService, useValue: messageServiceMock},
        {provide: LocalStorageService, useValue: localStorageServiceMock}
      ]
    });

    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('should initialize with default scale if nothing in storage', () => {
    localStorageServiceMock.get.mockReturnValueOnce(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    expect(service.scaleFactor).toBe(1.0);
  });

  it('should initialize with saved scale from storage', () => {
    localStorageServiceMock.get.mockReturnValueOnce(1.5);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    expect(service.scaleFactor).toBe(1.5);
  });

  it('should ignore NaN value from storage', () => {
    localStorageServiceMock.get.mockReturnValueOnce(NaN);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    expect(service.scaleFactor).toBe(1.0);
  });

  it('initScaleValue sets scaleFactor', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service.initScaleValue(2.2);
    expect(service.scaleFactor).toBe(2.2);
    service.initScaleValue(undefined);
    expect(service.scaleFactor).toBe(1.0);
  });

  it('setScale updates scaleFactor and emits on scaleChange$', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    const spy = vi.fn();
    service.scaleChange$.pipe(take(1)).subscribe(spy);
    service.setScale(1.8);
    expect(service.scaleFactor).toBe(1.8);
    expect(spy).toHaveBeenCalledWith(1.8);
  });

  it('currentCardSize returns correct dimensions', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service.scaleFactor = 2;
    expect(service.currentCardSize).toEqual({width: 270, height: 440});
    service.scaleFactor = 0.5;
    expect(service.currentCardSize).toEqual({width: 68, height: 110});
  });

  it('gridColumnMinWidth returns correct string', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service.scaleFactor = 1.23;
    expect(service.gridColumnMinWidth).toBe(`${Math.round(135 * 1.23)}px`);
  });

  it('saveScalePreference saves to localStorage and shows success message', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service['saveScalePreference'](1.25);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('coverScalePreference', 1.25);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'Cover Size Saved',
      detail: expect.stringContaining('1.25')
    }));
  });

  it('saveScalePreference handles errors and shows error message', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    localStorageServiceMock.set.mockImplementation(() => {
      throw new Error('fail');
    });
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service['saveScalePreference'](1.1);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      summary: 'Save Failed'
    }));
  });

  it('setScale triggers debounced saveScalePreference', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service.setScale(1.7);
    vi.advanceTimersByTime(1000);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('coverScalePreference', 1.7);
  });

  it('debounced save only triggers once for rapid setScale calls', () => {
    localStorageServiceMock.get.mockReturnValue(null);
    const injector = TestBed.inject(EnvironmentInjector);
    const service = runInInjectionContext(injector, () => TestBed.inject(CoverScalePreferenceService));
    service.setScale(1.1);
    service.setScale(1.2);
    service.setScale(1.3);
    vi.advanceTimersByTime(1000);
    expect(localStorageServiceMock.set).toHaveBeenCalledTimes(1);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('coverScalePreference', 1.3);
  });
});
