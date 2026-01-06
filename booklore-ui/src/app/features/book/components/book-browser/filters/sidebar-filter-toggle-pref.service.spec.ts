import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {SidebarFilterTogglePrefService} from './sidebar-filter-toggle-pref.service';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../../../shared/service/local-storage-service';
import {take} from 'rxjs/operators';

describe('SidebarFilterTogglePrefService', () => {
  let messageServiceMock: any;
  let localStorageServiceMock: any;
  let originalInnerWidth: number;

  beforeEach(() => {
    messageServiceMock = {add: vi.fn()};
    localStorageServiceMock = {
      set: vi.fn(),
      get: vi.fn()
    };
    originalInnerWidth = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', {writable: true, configurable: true, value: 1024});

    TestBed.configureTestingModule({
      providers: [
        SidebarFilterTogglePrefService,
        {provide: MessageService, useValue: messageServiceMock},
        {provide: LocalStorageService, useValue: localStorageServiceMock}
      ]
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'innerWidth', {writable: true, configurable: true, value: originalInnerWidth});
  });

  function createService(): SidebarFilterTogglePrefService {
    const injector = TestBed.inject(EnvironmentInjector);
    return runInInjectionContext(injector, () => TestBed.inject(SidebarFilterTogglePrefService));
  }

  it('should initialize with true if no saved value and not narrow', () => {
    localStorageServiceMock.get.mockReturnValueOnce(null);
    const service = createService();
    expect(service.selectedShowFilter).toBe(true);
  });

  it('should initialize with saved value if present and not narrow', () => {
    localStorageServiceMock.get.mockReturnValueOnce(false);
    const service = createService();
    expect(service.selectedShowFilter).toBe(false);
  });

  it('should initialize with false if window is narrow', () => {
    Object.defineProperty(window, 'innerWidth', {writable: true, configurable: true, value: 600});
    const service = createService();
    expect(service.selectedShowFilter).toBe(false);
  });

  it('should emit value on showFilter$', async () => {
    localStorageServiceMock.get.mockReturnValueOnce(null);
    const service = createService();
    const val = await service.showFilter$.pipe(take(1)).toPromise();
    expect(val).toBe(true);
  });

  it('should update selectedShowFilter and persist preference', () => {
    localStorageServiceMock.get.mockReturnValueOnce(null);
    const service = createService();
    service.selectedShowFilter = false;
    expect(service.selectedShowFilter).toBe(false);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('showSidebarFilter', false);
  });

  it('should not persist if value is unchanged', () => {
    localStorageServiceMock.get.mockReturnValueOnce(true);
    const service = createService();
    localStorageServiceMock.set.mockClear();
    service.selectedShowFilter = true;
    expect(localStorageServiceMock.set).not.toHaveBeenCalled();
  });

  it('should toggle selectedShowFilter', () => {
    localStorageServiceMock.get.mockReturnValueOnce(true);
    const service = createService();
    service.toggle();
    expect(service.selectedShowFilter).toBe(false);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('showSidebarFilter', false);
    service.toggle();
    expect(service.selectedShowFilter).toBe(true);
    expect(localStorageServiceMock.set).toHaveBeenCalledWith('showSidebarFilter', true);
  });

  it('should handle error in savePreference and show error message', () => {
    localStorageServiceMock.get.mockReturnValueOnce(null);
    localStorageServiceMock.set.mockImplementation(() => {
      throw new Error('fail');
    });
    const service = createService();
    service.selectedShowFilter = false;
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      summary: 'Save Failed'
    }));
  });

  it('should call loadFromStorage and set correct value for wide screen', () => {
    localStorageServiceMock.get.mockReturnValueOnce(true);
    const service = createService();
    expect(service.selectedShowFilter).toBe(true);
  });

  it('should call loadFromStorage and set correct value for narrow screen', () => {
    Object.defineProperty(window, 'innerWidth', {writable: true, configurable: true, value: 500});
    const service = createService();
    expect(service.selectedShowFilter).toBe(false);
  });
});

