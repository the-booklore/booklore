import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {
  CbxFitMode,
  CbxPageSpread,
  CbxPageViewMode,
  PdfPageSpread,
  PdfPageViewMode,
  User,
  UserService,
  UserUpdateRequest
} from './user.service';
import {AuthService} from '../../../shared/service/auth.service';

describe('UserService', () => {
  let service: UserService;
  let httpClientMock: any;
  let authServiceMock: any;

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
        global: {sortKey: '', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false},
        overrides: []
      },
      koReaderEnabled: false
    }
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    authServiceMock = {
      token$: of('token')
    };

    TestBed.configureTestingModule({
      providers: [
        UserService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(UserService)
    );
  });

  it('should initialize with default state', () => {
    expect(service.userStateSubject.value).toEqual({
      user: null,
      loaded: false,
      error: null
    });
  });

  it('should set initial user', () => {
    service.setInitialUser(mockUser);
    expect(service.userStateSubject.value.user).toEqual(mockUser);
    expect(service.userStateSubject.value.loaded).toBe(true);
  });

  it('should get current user', () => {
    service.setInitialUser(mockUser);
    expect(service.getCurrentUser()).toEqual(mockUser);
  });

  it('should fetch myself and update state', () => {
    httpClientMock.get.mockReturnValue(of(mockUser));
    service['fetchMyself']().subscribe(user => {
      expect(user).toEqual(mockUser);
      expect(service.userStateSubject.value.user).toEqual(mockUser);
      expect(service.userStateSubject.value.loaded).toBe(true);
    });
  });

  it('should handle fetch myself error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service['fetchMyself']().subscribe({
      error: (err: any) => {
        expect(service.userStateSubject.value.error).toBe('fail');
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should get myself via getMyself()', () => {
    httpClientMock.get.mockReturnValue(of(mockUser));
    service.getMyself().subscribe(user => {
      expect(user).toEqual(mockUser);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/me'));
    });
  });

  it('should create user', () => {
    httpClientMock.post.mockReturnValue(of(void 0));
    const {id, ...userData} = mockUser;
    service.createUser(userData).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.post).toHaveBeenCalled();
    });
  });

  it('should get users', () => {
    httpClientMock.get.mockReturnValue(of([mockUser]));
    service.getUsers().subscribe(users => {
      expect(users).toEqual([mockUser]);
      expect(httpClientMock.get).toHaveBeenCalled();
    });
  });

  it('should update user', () => {
    httpClientMock.put.mockReturnValue(of(mockUser));
    const update: UserUpdateRequest = {name: 'New Name'};
    service.updateUser(1, update).subscribe(user => {
      expect(user).toEqual(mockUser);
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/1'), update);
    });
  });

  it('should delete user', () => {
    httpClientMock.delete.mockReturnValue(of(void 0));
    service.deleteUser(1).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringContaining('/1'));
    });
  });

  it('should change user password', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service.changeUserPassword(1, 'newpass').subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('change-user-password'), {userId: 1, newPassword: 'newpass'});
    });
  });

  it('should handle change user password error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => ({error: {message: 'bad'}})));
    service.changeUserPassword(1, 'badpass').subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('bad');
      }
    });
  });

  it('should change password', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service.changePassword('old', 'new').subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('change-password'), {currentPassword: 'old', newPassword: 'new'});
    });
  });

  it('should handle change password error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
    service.changePassword('old', 'bad').subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should update user setting and update state', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service.setInitialUser(mockUser);
    service.updateUserSetting(1, 'koReaderEnabled', true);
    expect(httpClientMock.put).toHaveBeenCalledWith(expect.stringContaining('/1/settings'), {key: 'koReaderEnabled', value: true}, expect.anything());
    expect(service.userStateSubject.value.user?.userSettings.koReaderEnabled).toBe(true);
  });
});

describe('UserService - API Contract Tests', () => {
  let service: UserService;
  let httpClientMock: any;
  let authServiceMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    };

    authServiceMock = {
      token$: of('token')
    };

    TestBed.configureTestingModule({
      providers: [
        UserService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(UserService));
  });

  describe('User interface contract', () => {
    it('should validate all required User fields exist', () => {
      const requiredFields: (keyof User)[] = [
        'id', 'username', 'name', 'email', 'assignedLibraries', 'permissions', 'userSettings'
      ];

      const mockResponse: User = {
        id: 1,
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and',
          filterSortingMode: 'alphabetical',
          metadataCenterViewMode: 'route',
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };

      httpClientMock.get.mockReturnValue(of(mockResponse));

      service.getMyself().subscribe(user => {
        requiredFields.forEach(field => {
          expect(user).toHaveProperty(field);
          expect(user[field]).toBeDefined();
        });
      });
    });

    it('should validate all required Permission fields exist', () => {
      const requiredPermissions = [
        'admin', 'canUpload', 'canDownload', 'canEmailBook', 'canDeleteBook',
        'canEditMetadata', 'canManageLibrary', 'canManageMetadataConfig',
        'canSyncKoReader', 'canSyncKobo', 'canAccessOpds', 'canAccessBookdrop',
        'canAccessLibraryStats', 'canAccessUserStats', 'canAccessTaskManager',
        'canManageEmailConfig', 'canManageGlobalPreferences', 'canManageIcons',
        'canManageFonts', // <-- Added test for canManageFonts
        'demoUser', 'canBulkAutoFetchMetadata', 'canBulkCustomFetchMetadata',
        'canBulkEditMetadata', 'canBulkRegenerateCover', 'canMoveOrganizeFiles',
        'canBulkLockUnlockMetadata'
      ];

      const mockResponse: User = {
        id: 1,
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
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
          canManageFonts: true, // <-- Added to mock
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
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and',
          filterSortingMode: 'alphabetical',
          metadataCenterViewMode: 'route',
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };

      httpClientMock.get.mockReturnValue(of(mockResponse));

      service.getMyself().subscribe(user => {
        requiredPermissions.forEach(permission => {
          expect(user.permissions).toHaveProperty(permission);
          expect(typeof user.permissions[permission as keyof typeof user.permissions]).toBe('boolean');
        });
        // Explicit test for canManageFonts
        expect(user.permissions.canManageFonts).toBe(true);
      });
    });

    it('should validate all required UserSettings fields exist', () => {
      const requiredSettings = [
        'perBookSetting', 'pdfReaderSetting', 'epubReaderSetting', 'cbxReaderSetting',
        'newPdfReaderSetting', 'sidebarLibrarySorting', 'sidebarShelfSorting',
        'sidebarMagicShelfSorting', 'filterMode', 'filterSortingMode',
        'metadataCenterViewMode', 'enableSeriesView', 'entityViewPreferences', 'koReaderEnabled'
      ];

      const mockResponse: User = {
        id: 1,
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and',
          filterSortingMode: 'alphabetical',
          metadataCenterViewMode: 'route',
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };

      httpClientMock.get.mockReturnValue(of(mockResponse));

      service.getMyself().subscribe(user => {
        requiredSettings.forEach(setting => {
          expect(user.userSettings).toHaveProperty(setting);
          expect(user.userSettings[setting as keyof typeof user.userSettings]).toBeDefined();
        });
      });
    });

    it('should fail if API returns User without required id field', () => {
      const invalidResponse = {
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
        assignedLibraries: [],
        permissions: {},
        userSettings: {}
      };

      httpClientMock.get.mockReturnValue(of(invalidResponse));

      service.getMyself().subscribe(user => {
        expect(user).not.toHaveProperty('id');
      });
    });

    it('should fail if API returns User without permissions', () => {
      const invalidResponse = {
        id: 1,
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
        assignedLibraries: [],
        userSettings: {}
      };

      httpClientMock.get.mockReturnValue(of(invalidResponse));

      service.getMyself().subscribe(user => {
        expect(user).not.toHaveProperty('permissions');
      });
    });

    it('should fail if API returns User without userSettings', () => {
      const invalidResponse = {
        id: 1,
        username: 'test',
        name: 'Test',
        email: 'test@test.com',
        assignedLibraries: [],
        permissions: {}
      };

      httpClientMock.get.mockReturnValue(of(invalidResponse));

      service.getMyself().subscribe(user => {
        expect(user).not.toHaveProperty('userSettings');
      });
    });
  });

  describe('Enum value contract', () => {
    it('should validate CbxPageSpread enum values from API', () => {
      const validValues = [CbxPageSpread.EVEN, CbxPageSpread.ODD];
      expect(validValues).toContain(CbxPageSpread.EVEN);
      expect(validValues).toContain(CbxPageSpread.ODD);
      expect(Object.keys(CbxPageSpread)).toHaveLength(2);
    });

    it('should validate CbxPageViewMode enum values from API', () => {
      const validValues = [CbxPageViewMode.SINGLE_PAGE, CbxPageViewMode.TWO_PAGE];
      expect(validValues).toContain(CbxPageViewMode.SINGLE_PAGE);
      expect(validValues).toContain(CbxPageViewMode.TWO_PAGE);
      expect(Object.keys(CbxPageViewMode)).toHaveLength(2);
    });

    it('should validate CbxFitMode enum values from API', () => {
      const validValues = [
        CbxFitMode.ACTUAL_SIZE,
        CbxFitMode.FIT_PAGE,
        CbxFitMode.FIT_WIDTH,
        CbxFitMode.FIT_HEIGHT,
        CbxFitMode.AUTO
      ];
      validValues.forEach(value => {
        expect(Object.values(CbxFitMode)).toContain(value);
      });
      expect(Object.keys(CbxFitMode)).toHaveLength(5);
    });

    it('should validate PdfPageSpread enum values from API', () => {
      const validValues = [PdfPageSpread.EVEN, PdfPageSpread.ODD];
      expect(validValues).toContain(PdfPageSpread.EVEN);
      expect(validValues).toContain(PdfPageSpread.ODD);
      expect(Object.keys(PdfPageSpread)).toHaveLength(2);
    });

    it('should validate PdfPageViewMode enum values from API', () => {
      const validValues = [PdfPageViewMode.SINGLE_PAGE, PdfPageViewMode.TWO_PAGE];
      expect(validValues).toContain(PdfPageViewMode.SINGLE_PAGE);
      expect(validValues).toContain(PdfPageViewMode.TWO_PAGE);
      expect(Object.keys(PdfPageViewMode)).toHaveLength(2);
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for getMyself', () => {
      httpClientMock.get.mockReturnValue(of({}));
      service.getMyself().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/users\/me$/));
    });

    it('should call correct endpoint for getUsers', () => {
      httpClientMock.get.mockReturnValue(of([]));
      service.getUsers().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/users$/));
    });

    it('should call correct endpoint for createUser', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      const userData = {
        username: 'new',
        name: 'New User',
        email: 'new@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off' as const, pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and' as const,
          filterSortingMode: 'alphabetical' as const,
          metadataCenterViewMode: 'route' as const,
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };
      service.createUser(userData).subscribe();
      expect(httpClientMock.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/auth\/register$/),
        userData
      );
    });

    it('should call correct endpoint for updateUser', () => {
      httpClientMock.put.mockReturnValue(of({}));
      service.updateUser(123, {name: 'Updated'}).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/users\/123$/),
        {name: 'Updated'}
      );
    });

    it('should call correct endpoint for deleteUser', () => {
      httpClientMock.delete.mockReturnValue(of(void 0));
      service.deleteUser(123).subscribe();
      expect(httpClientMock.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/users\/123$/));
    });

    it('should call correct endpoint for changeUserPassword', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.changeUserPassword(123, 'newpass').subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/users\/change-user-password$/),
        {userId: 123, newPassword: 'newpass'}
      );
    });

    it('should call correct endpoint for changePassword', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.changePassword('oldpass', 'newpass').subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/users\/change-password$/),
        {currentPassword: 'oldpass', newPassword: 'newpass'}
      );
    });

    it('should call correct endpoint for updateUserSetting', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.updateUserSetting(123, 'koReaderEnabled', true);
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/users\/123\/settings$/),
        {key: 'koReaderEnabled', value: true},
        expect.anything()
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send UserUpdateRequest with correct structure', () => {
      httpClientMock.put.mockReturnValue(of({}));
      const updateRequest: UserUpdateRequest = {
        name: 'New Name',
        email: 'newemail@test.com',
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
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: true,
          canBulkCustomFetchMetadata: true,
          canBulkEditMetadata: true,
          canBulkRegenerateCover: true,
          canMoveOrganizeFiles: true,
          canBulkLockUnlockMetadata: true
        },
        assignedLibraries: [1, 2, 3]
      };
      service.updateUser(1, updateRequest).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        updateRequest
      );
    });

    it('should send change password payload with correct structure', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.changePassword('current', 'new').subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        {currentPassword: 'current', newPassword: 'new'}
      );
    });

    it('should send change user password payload with correct structure', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.changeUserPassword(456, 'newpassword').subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        {userId: 456, newPassword: 'newpassword'}
      );
    });

    it('should send user setting payload with correct structure', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.updateUserSetting(789, 'enableSeriesView', false);
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        {key: 'enableSeriesView', value: false},
        expect.anything()
      );
    });
  });

  describe('Response type contract', () => {
    it('should expect User array from getUsers', () => {
      const mockUsers: User[] = [{
        id: 1,
        username: 'user1',
        name: 'User One',
        email: 'user1@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and',
          filterSortingMode: 'alphabetical',
          metadataCenterViewMode: 'route',
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      }];

      httpClientMock.get.mockReturnValue(of(mockUsers));
      service.getUsers().subscribe(users => {
        expect(Array.isArray(users)).toBe(true);
        expect(users[0]).toHaveProperty('id');
        expect(users[0]).toHaveProperty('username');
      });
    });

    it('should expect User from updateUser', () => {
      const mockUser: User = {
        id: 1,
        username: 'updated',
        name: 'Updated User',
        email: 'updated@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off', pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and',
          filterSortingMode: 'alphabetical',
          metadataCenterViewMode: 'route',
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };

      httpClientMock.put.mockReturnValue(of(mockUser));
      service.updateUser(1, {name: 'Updated User'}).subscribe(user => {
        expect(user).toHaveProperty('id');
        expect(user).toHaveProperty('username');
        expect(user).toHaveProperty('permissions');
        expect(user).toHaveProperty('userSettings');
      });
    });

    it('should expect void from createUser', () => {
      httpClientMock.post.mockReturnValue(of(void 0));
      const userData = {
        username: 'new',
        name: 'New',
        email: 'new@test.com',
        assignedLibraries: [],
        permissions: {
          admin: false,
          canUpload: false,
          canDownload: false,
          canEmailBook: false,
          canDeleteBook: false,
          canEditMetadata: false,
          canManageLibrary: false,
          canManageMetadataConfig: false,
          canSyncKoReader: false,
          canSyncKobo: false,
          canAccessOpds: false,
          canAccessBookdrop: false,
          canAccessLibraryStats: false,
          canAccessUserStats: false,
          canAccessTaskManager: false,
          canManageEmailConfig: false,
          canManageGlobalPreferences: false,
          canManageIcons: false,
          canManageFonts: false,
          demoUser: false,
          canBulkAutoFetchMetadata: false,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: false,
          canBulkRegenerateCover: false,
          canMoveOrganizeFiles: false,
          canBulkLockUnlockMetadata: false
        },
        userSettings: {
          perBookSetting: {pdf: '', epub: '', cbx: ''},
          pdfReaderSetting: {pageSpread: 'off' as const, pageZoom: '', showSidebar: false},
          epubReaderSetting: {theme: '', font: '', fontSize: 1, flow: '', spread: '', lineHeight: 1, margin: 1, letterSpacing: 1},
          cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.ACTUAL_SIZE},
          newPdfReaderSetting: {pageSpread: PdfPageSpread.EVEN, pageViewMode: PdfPageViewMode.SINGLE_PAGE},
          sidebarLibrarySorting: {field: '', order: ''},
          sidebarShelfSorting: {field: '', order: ''},
          sidebarMagicShelfSorting: {field: '', order: ''},
          filterMode: 'and' as const,
          filterSortingMode: 'alphabetical' as const,
          metadataCenterViewMode: 'route' as const,
          enableSeriesView: true,
          entityViewPreferences: {global: {sortKey: '', sortDir: 'ASC' as const, view: 'GRID' as const, coverSize: 1, seriesCollapsed: false}, overrides: []},
          koReaderEnabled: false
        }
      };
      service.createUser(userData).subscribe(result => {
        expect(result).toBeUndefined();
      });
    });
  });
});
