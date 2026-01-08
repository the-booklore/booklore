import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BehaviorSubject, of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {HardcoverSettingsComponent} from './hardcover-settings-component';
import {HardcoverSyncSettingsService} from './hardcover-sync-settings.service';
import {UserService, UserState} from '../../../user-management/user.service';

describe('HardcoverSettingsComponent', () => {
  let fixture: ComponentFixture<HardcoverSettingsComponent>;
  let component: HardcoverSettingsComponent;
  let settingsServiceMock: any;
  let messageServiceMock: any;
  let userState$: BehaviorSubject<UserState>;

  const makeState = (permissions: Partial<UserState['user']> = {}): UserState => ({
    loaded: true,
    error: null,
    user: {
      id: 1,
      username: 'u',
      name: 'User',
      email: 'u@example.com',
      assignedLibraries: [],
      provisioningMethod: 'LOCAL',
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
        demoUser: false,
        canBulkAutoFetchMetadata: false,
        canBulkCustomFetchMetadata: false,
        canBulkEditMetadata: false,
        canBulkRegenerateCover: false,
        canMoveOrganizeFiles: false,
        canBulkLockUnlockMetadata: false
      },
      userSettings: {} as any,
      ...permissions
    }
  });

  beforeEach(async () => {
    settingsServiceMock = {
      getSettings: vi.fn(),
      updateSettings: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };
    userState$ = new BehaviorSubject<UserState>(makeState());

    await TestBed.configureTestingModule({
      imports: [HardcoverSettingsComponent],
      providers: [
        {provide: HardcoverSyncSettingsService, useValue: settingsServiceMock},
        {provide: MessageService, useValue: messageServiceMock},
        {provide: UserService, useValue: {userState$: userState$}}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(HardcoverSettingsComponent);
    component = fixture.componentInstance;
  });

  it('should load settings when user has permission', () => {
    settingsServiceMock.getSettings.mockReturnValue(of({hardcoverSyncEnabled: true, hardcoverApiKey: 'key'}));
    userState$.next(makeState({
      permissions: {
        ...makeState().user!.permissions,
        canSyncKobo: true
      }
    }) as UserState);

    fixture.detectChanges();

    expect(settingsServiceMock.getSettings).toHaveBeenCalled();
    expect(component.hardcoverSyncEnabled).toBe(true);
    expect(component.hardcoverApiKey).toBe('key');
  });

  it('should not load settings without permission', () => {
    settingsServiceMock.getSettings.mockReturnValue(of({}));
    userState$.next(makeState());
    fixture.detectChanges();
    expect(settingsServiceMock.getSettings).not.toHaveBeenCalled();
  });

  it('should update settings on toggle', () => {
    settingsServiceMock.updateSettings.mockReturnValue(of({hardcoverSyncEnabled: false, hardcoverApiKey: 'k'}));
    component.hardcoverSyncEnabled = false;
    component.hardcoverApiKey = 'k';

    component.onHardcoverSyncToggle();

    expect(settingsServiceMock.updateSettings).toHaveBeenCalledWith({
      hardcoverSyncEnabled: false,
      hardcoverApiKey: 'k'
    });
    expect(messageServiceMock.add).toHaveBeenCalled();
  });

  it('should handle update error', () => {
    settingsServiceMock.updateSettings.mockReturnValue(throwError(() => new Error('fail')));
    component.hardcoverSyncEnabled = true;
    component.hardcoverApiKey = 'k';

    component.onHardcoverApiKeyChange();

    expect(settingsServiceMock.updateSettings).toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error'
    }));
  });

  it('should handle load error', () => {
    settingsServiceMock.getSettings.mockReturnValue(throwError(() => new Error('fail')));
    userState$.next(makeState({
      permissions: {
        ...makeState().user!.permissions,
        admin: true
      }
    }) as UserState);

    fixture.detectChanges();

    expect(settingsServiceMock.getSettings).toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error'
    }));
  });
});
