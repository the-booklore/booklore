import {beforeEach, describe, expect, it, vi, afterEach} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {BehaviorSubject, firstValueFrom, of, Subject} from 'rxjs';
import {SeriesCollapseFilter} from './SeriesCollapseFilter';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../../settings/user-management/user.service';
import {BookState} from '../../../model/state/book-state.model';
import {Book} from '../../../model/book.model';

describe('SeriesCollapseFilter', () => {
  let userServiceMock: any;
  let messageServiceMock: any;
  let userStateSubject: BehaviorSubject<any>;
  let currentUser: any;

  beforeEach(() => {
    userStateSubject = new BehaviorSubject({
      user: null,
      loaded: false,
      error: null
    });

    currentUser = {
      id: 1,
      userSettings: {
        entityViewPreferences: {
          global: {seriesCollapsed: false},
          overrides: []
        }
      }
    };

    userServiceMock = {
      userState$: userStateSubject.asObservable(),
      getCurrentUser: vi.fn(() => currentUser),
      updateUserSetting: vi.fn()
    };

    messageServiceMock = {add: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        SeriesCollapseFilter,
        {provide: UserService, useValue: userServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  function createService(): SeriesCollapseFilter {
    const injector = TestBed.inject(EnvironmentInjector);
    return runInInjectionContext(injector, () => TestBed.inject(SeriesCollapseFilter));
  }

  it('should initialize with default collapsed=false', () => {
    const service = createService();
    expect(service.isSeriesCollapsed).toBe(false);
  });

  it('should apply global preference on userState$ emit', () => {
    currentUser.userSettings.entityViewPreferences.global.seriesCollapsed = true;
    userServiceMock.getCurrentUser.mockReturnValue(currentUser);
    const service = createService();
    userStateSubject.next({user: currentUser, loaded: true, error: null});
    expect(service.isSeriesCollapsed).toBe(true);
  });

  it('should apply override preference for context', () => {
    currentUser.userSettings.entityViewPreferences.overrides = [
      {entityType: 'LIBRARY', entityId: 42, preferences: {seriesCollapsed: true}}
    ];
    userServiceMock.getCurrentUser.mockReturnValue(currentUser);
    const service = createService();
    service.setContext('LIBRARY', 42);
    userStateSubject.next({user: currentUser, loaded: true, error: null});
    expect(service.isSeriesCollapsed).toBe(true);
  });

  it('should fallback to old seriesCollapse field for global', () => {
    currentUser.userSettings.entityViewPreferences.global = {seriesCollapse: true};
    delete currentUser.userSettings.entityViewPreferences.global.seriesCollapsed;
    userServiceMock.getCurrentUser.mockReturnValue(currentUser);
    const service = createService();
    userStateSubject.next({user: currentUser, loaded: true, error: null});
    expect(service.isSeriesCollapsed).toBe(true);
  });

  it('should fallback to old seriesCollapse field for override', () => {
    currentUser.userSettings.entityViewPreferences.overrides = [
      {entityType: 'SHELF', entityId: 99, preferences: {seriesCollapse: true}}
    ];
    userServiceMock.getCurrentUser.mockReturnValue(currentUser);
    const service = createService();
    service.setContext('SHELF', 99);
    userStateSubject.next({user: currentUser, loaded: true, error: null});
    expect(service.isSeriesCollapsed).toBe(true);
  });

  it('setCollapsed should update value and trigger persistence after debounce', () => {
    const service = createService();
    service.setCollapsed(true);
    expect(service.isSeriesCollapsed).toBe(true);
    vi.advanceTimersByTime(500);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'Preference Saved',
      detail: expect.stringContaining('enabled')
    }));
  });

  it('setCollapsed should persist override if context set', () => {
    const service = createService();
    service.setContext('MAGIC_SHELF', 123);
    service.setCollapsed(true);
    vi.advanceTimersByTime(500);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      1,
      'entityViewPreferences',
      expect.objectContaining({
        overrides: expect.arrayContaining([
          expect.objectContaining({
            entityType: 'MAGIC_SHELF',
            entityId: 123,
            preferences: expect.objectContaining({seriesCollapsed: true})
          })
        ])
      })
    );
  });

  it('setCollapsed should persist global if no context', () => {
    const service = createService();
    service.setCollapsed(true);
    vi.advanceTimersByTime(500);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      1,
      'entityViewPreferences',
      expect.objectContaining({
        global: expect.objectContaining({seriesCollapsed: true})
      })
    );
  });

  it('should not persist if no user', () => {
    userServiceMock.getCurrentUser.mockReturnValue(null);
    const service = createService();
    service.setCollapsed(true);
    vi.advanceTimersByTime(500);
    expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
  });

  it('should filter books and collapse series', async () => {
    const service = createService();
    service.setCollapsed(true);

    const books: Book[] = [
      {id: 1, metadata: {seriesName: 'A', seriesNumber: 2}, title: 'B1'} as any,
      {id: 2, metadata: {seriesName: 'A', seriesNumber: 1}, title: 'B2'} as any,
      {id: 3, metadata: {seriesName: 'B', seriesNumber: 1}, title: 'B3'} as any,
      {id: 4, metadata: {}, title: 'B4'} as any
    ];
    const state: BookState = {books, loaded: true, error: null};

    const result = await firstValueFrom(service.filter(state));
    expect(result && result.books).toBeTruthy();
    if (!result || !result.books) throw new Error('No books in result');
    expect(result.books.length).toBe(3);
    // Series A collapsed to one book with seriesBooks of length 2
    const a = result.books.find(b => b.metadata?.seriesName === 'A');
    expect(a).toBeDefined();
    expect(a?.seriesBooks?.length).toBe(2);
    expect(a?.seriesCount).toBe(2);
    // Series B collapsed to one book with seriesBooks of length 1
    const b = result.books.find(b => b.metadata?.seriesName === 'B');
    expect(b).toBeDefined();
    expect(b?.seriesBooks?.length).toBe(1);
    expect(b?.seriesCount).toBe(1);
    // Non-series book remains
    expect(result.books.some(b => b['title'] === 'B4')).toBe(true);
  });

  it('should not collapse series if forceExpandSeries=true', async () => {
    const service = createService();
    service.setCollapsed(true);

    const books: Book[] = [
      {id: 1, metadata: {seriesName: 'A', seriesNumber: 2}, title: 'B1'} as any,
      {id: 2, metadata: {seriesName: 'A', seriesNumber: 1}, title: 'B2'} as any
    ];
    const state: BookState = {books, loaded: true, error: null};

    const result = await firstValueFrom(service.filter(state, true));
    expect(result && result.books).toBeTruthy();
    if (!result || !result.books) throw new Error('No books in result');
    expect(result.books.length).toBe(2);
  });

  it('should return original state if books is null', async () => {
    const service = createService();
    service.setCollapsed(true);
    const state: BookState = {books: null, loaded: true, error: null};
    const result = await firstValueFrom(service.filter(state));
    expect(result).toBe(state);
  });

  it('should not collapse if isSeriesCollapsed is false', async () => {
    const service = createService();
    service.setCollapsed(false);
    const books: Book[] = [
      {id: 1, metadata: {seriesName: 'A', seriesNumber: 1}, title: 'B1'} as any
    ];
    const state: BookState = {books, loaded: true, error: null};
    const result = await firstValueFrom(service.filter(state));
    expect(result && result.books).toBeTruthy();
    if (!result || !result.books) throw new Error('No books in result');
    expect(result.books.length).toBe(1);
    expect(result.books[0]?.seriesBooks).toBeUndefined();
  });

  it('should clean up destroy$ on ngOnDestroy', () => {
    const service = createService();
    const spy = vi.spyOn(service['destroy$'], 'next');
    service.ngOnDestroy();
    expect(spy).toHaveBeenCalled();
  });

  it('setContext(null, null) resets context and applies preference', () => {
    const service = createService();
    const applyPrefSpy = vi.spyOn(service as any, 'applyPreference');
    service.setContext(null, null);
    expect((service as any).currentContext).toBeNull();
    expect(applyPrefSpy).toHaveBeenCalled();
  });
});

