import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {ReaderPreferencesService} from './reader-preferences.service';
import {CbxFitMode, CbxPageSpread, CbxPageViewMode, PdfPageSpread, PdfPageViewMode, User, UserService} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {of, Subject} from 'rxjs';

const mockUser: User = {
  id: 1,
  username: 'testuser',
  name: 'Test User',
  email: 'test@example.com',
  assignedLibraries: [],
  permissions: {
    admin: true,
    canUpload: true,
    canDownload: true,
    canEmailBook: true,
    canDeleteBook: true,
    canEditMetadata: true,
    canManageLibrary: true,
    canManageMetadataConfig: true,
    canSyncKoReader: true,
    canSyncKobo: true,
    canAccessOpds: true,
    canAccessBookdrop: true,
    canAccessLibraryStats: true,
    canAccessUserStats: true,
    canAccessTaskManager: true,
    canManageEmailConfig: true,
    canManageGlobalPreferences: true,
    canManageIcons: true,
    canManageFonts: true,
    demoUser: false,
    canBulkAutoFetchMetadata: true,
    canBulkCustomFetchMetadata: true,
    canBulkEditMetadata: true,
    canBulkRegenerateCover: true,
    canMoveOrganizeFiles: true,
    canBulkLockUnlockMetadata: true
  },
  userSettings: {
    perBookSetting: {pdf: '', epub: '', cbx: ''},
    pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
    epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
    ebookReaderSetting: {
      lineHeight: 1,
      justify: false,
      hyphenate: false,
      maxColumnCount: 1,
      gap: 0,
      fontSize: 1,
      theme: '',
      maxInlineSize: 0,
      maxBlockSize: 0,
      fontFamily: '',
      isDark: false,
      flow: 'paginated'
    },
    cbxReaderSetting: {
      pageSpread: CbxPageSpread.EVEN,
      pageViewMode: CbxPageViewMode.SINGLE_PAGE,
      fitMode: CbxFitMode.ACTUAL_SIZE
    },
    newPdfReaderSetting: {
      pageSpread: PdfPageSpread.EVEN,
      pageViewMode: PdfPageViewMode.SINGLE_PAGE
    },
    sidebarLibrarySorting: {field: '', order: ''},
    sidebarShelfSorting: {field: '', order: ''},
    sidebarMagicShelfSorting: {field: '', order: ''},
    filterMode: 'and',
    filterSortingMode: 'alphabetical',
    metadataCenterViewMode: 'route',
    enableSeriesView: true,
    entityViewPreferences: {
      global: {sortKey: '', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false, overlayBookType: false},
      overrides: []
    },
    koReaderEnabled: false,
    autoSaveMetadata: false
  }
};

describe('ReaderPreferencesService', () => {
  let service: ReaderPreferencesService;
  let userServiceMock: any;
  let messageServiceMock: any;
  let destroy$: Subject<void>;

  beforeEach(() => {
    destroy$ = new Subject<void>();
    userServiceMock = {
      userState$: of({user: mockUser, loaded: true}),
      updateUserSetting: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: userServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReaderPreferencesService));
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should set currentUser from userService', () => {
    // @ts-ignore
    expect(service.currentUser).toEqual(mockUser);
  });

  it('should update preference and call updateUserSetting', () => {
    const path = ['cbxReaderSetting', 'fitMode'];
    const value = CbxFitMode.FIT_PAGE;
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    service.updatePreference(path, value);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      mockUser.id,
      'cbxReaderSetting',
      expect.objectContaining({fitMode: value})
    );
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({severity: 'success'})
    );
  });

  it('should update nested preference', () => {
    const path = ['entityViewPreferences', 'global', 'sortKey'];
    const value = 'title';
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    service.updatePreference(path, value);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      mockUser.id,
      'entityViewPreferences',
      expect.objectContaining({
        global: expect.objectContaining({sortKey: value})
      })
    );
    expect(messageServiceMock.add).toHaveBeenCalled();
  });

  it('should not update if currentUser is null', () => {
    service['currentUser'] = null;
    service.updatePreference(['cbxReaderSetting', 'fitMode'], CbxFitMode.FIT_PAGE);
    expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
    expect(messageServiceMock.add).not.toHaveBeenCalled();
  });

  it('should clean up on destroy', () => {
    const spy = vi.spyOn(service['destroy$'], 'next');
    service.ngOnDestroy();
    expect(spy).toHaveBeenCalled();
  });
});

describe('ReaderPreferencesService - API Contract Tests', () => {
  let service: ReaderPreferencesService;
  let userServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    userServiceMock = {
      userState$: of({user: mockUser, loaded: true}),
      updateUserSetting: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: userServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(ReaderPreferencesService));
  });

  it('should call updateUserSetting with correct arguments for root-level setting', () => {
    const path = ['koReaderEnabled'];
    const value = true;
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    service.updatePreference(path, value);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      mockUser.id,
      'koReaderEnabled',
      value
    );
  });

  it('should call updateUserSetting with correct arguments for nested setting', () => {
    const path = ['pdfReaderSetting', 'pageZoom'];
    const value = '150%';
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    service.updatePreference(path, value);
    expect(userServiceMock.updateUserSetting).toHaveBeenCalledWith(
      mockUser.id,
      'pdfReaderSetting',
      expect.objectContaining({pageZoom: value})
    );
  });

  it('should show success message after updating preference', () => {
    const path = ['epubReaderSetting', 'fontSize'];
    const value = 18;
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    service.updatePreference(path, value);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        summary: 'Preferences Updated'
      })
    );
  });

  it('should not throw if path is empty', () => {
    service['currentUser'] = JSON.parse(JSON.stringify(mockUser));
    expect(() => service.updatePreference([], 'value')).not.toThrow();
  });
});

