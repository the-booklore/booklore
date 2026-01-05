import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {ConfirmationService, MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {MagicShelf, MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {UserService} from "../../settings/user-management/user.service";
import {LoadingService} from '../../../core/services/loading.service';
import {LibraryShelfMenuService} from './library-shelf-menu.service';
import {Library} from '../model/library.model';
import {Shelf} from '../model/shelf.model';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

describe('LibraryShelfMenuService', () => {
  let service: LibraryShelfMenuService;
  let confirmationService: any;
  let messageService: any;
  let libraryService: any;
  let shelfService: any;
  let taskHelperService: any;
  let router: any;
  let dialogLauncherService: any;
  let magicShelfService: any;
  let userService: any;
  let loadingService: any;

  const mockLibrary: Library = {id: 1, name: 'Lib1'} as Library;
  const mockShelf: Shelf = {id: 2, name: 'Shelf1'} as Shelf;
  const mockMagicShelf: MagicShelf = {id: 3, name: 'Magic1', isPublic: false} as MagicShelf;

  beforeEach(() => {
    confirmationService = {confirm: vi.fn()};
    messageService = {add: vi.fn()};
    libraryService = {
      refreshLibrary: vi.fn(),
      deleteLibrary: vi.fn()
    };
    shelfService = {
      deleteShelf: vi.fn()
    };
    taskHelperService = {
      refreshMetadataTask: vi.fn()
    };
    router = {navigate: vi.fn()};
    dialogLauncherService = {
      openLibraryEditDialog: vi.fn(),
      openLibraryMetadataFetchDialog: vi.fn(),
      openShelfEditDialog: vi.fn(),
      openMagicShelfEditDialog: vi.fn()
    };
    magicShelfService = {
      deleteShelf: vi.fn()
    };
    userService = {
      getCurrentUser: vi.fn()
    };
    loadingService = {
      show: vi.fn().mockReturnValue('loader-id'),
      hide: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        LibraryShelfMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: LibraryService, useValue: libraryService},
        {provide: ShelfService, useValue: shelfService},
        {provide: TaskHelperService, useValue: taskHelperService},
        {provide: Router, useValue: router},
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: UserService, useValue: userService},
        {provide: LoadingService, useValue: loadingService}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(LibraryShelfMenuService));
  });

  describe('initializeLibraryMenuItems', () => {
    it('should provide all menu items for a library', () => {
      const items = service.initializeLibraryMenuItems(mockLibrary);
      expect(items.length).toBe(1);
      const options = items[0].items!;
      expect(options.some(i => i.label === 'Edit Library')).toBe(true);
      expect(options.some(i => i.label === 'Re-scan Library')).toBe(true);
      expect(options.some(i => i.label === 'Custom Fetch Metadata')).toBe(true);
      expect(options.some(i => i.label === 'Auto Fetch Metadata')).toBe(true);
      expect(options.some(i => i.label === 'Delete Library')).toBe(true);
    });

    it('should call openLibraryEditDialog when Edit Library is clicked', () => {
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const edit = items[0].items!.find(i => i.label === 'Edit Library');
      edit!.command!({});
      expect(dialogLauncherService.openLibraryEditDialog).toHaveBeenCalledWith(mockLibrary.id);
    });

    it('should call openLibraryMetadataFetchDialog when Custom Fetch Metadata is clicked', () => {
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const fetch = items[0].items!.find(i => i.label === 'Custom Fetch Metadata');
      fetch!.command!({});
      expect(dialogLauncherService.openLibraryMetadataFetchDialog).toHaveBeenCalledWith(mockLibrary.id);
    });

    it('should call refreshMetadataTask when Auto Fetch Metadata is clicked', () => {
      taskHelperService.refreshMetadataTask.mockReturnValue(of({}));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const auto = items[0].items!.find(i => i.label === 'Auto Fetch Metadata');
      auto!.command!({});
      expect(taskHelperService.refreshMetadataTask).toHaveBeenCalledWith({
        refreshType: MetadataRefreshType.LIBRARY,
        libraryId: mockLibrary.id
      });
    });

    it('should show confirmation dialog for Re-scan Library', () => {
      libraryService.refreshLibrary.mockReturnValue(of({}));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const rescan = items[0].items!.find(i => i.label === 'Re-scan Library');
      rescan!.command!({});
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      expect(confirmCall.message).toContain(mockLibrary.name);
      expect(confirmCall.header).toBe('Confirmation');
    });

    it('should refresh library and show success message on accept', () => {
      libraryService.refreshLibrary.mockReturnValue(of({}));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const rescan = items[0].items!.find(i => i.label === 'Re-scan Library');
      rescan!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(libraryService.refreshLibrary).toHaveBeenCalledWith(mockLibrary.id);
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: 'Success',
        detail: 'Library refresh scheduled'
      });
    });

    it('should show error message if refreshLibrary fails', () => {
      libraryService.refreshLibrary.mockReturnValue(throwError(() => ({})));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const rescan = items[0].items!.find(i => i.label === 'Re-scan Library');
      rescan!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'Failed to refresh library'
      });
    });

    it('should show confirmation dialog for Delete Library', () => {
      libraryService.deleteLibrary.mockReturnValue(of({}));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const del = items[0].items!.find(i => i.label === 'Delete Library');
      del!.command!({});
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      expect(confirmCall.message).toContain(mockLibrary.name);
      expect(confirmCall.header).toBe('Confirmation');
    });

    it('should delete library, navigate, and show success message on accept', () => {
      libraryService.deleteLibrary.mockReturnValue(of({}));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const del = items[0].items!.find(i => i.label === 'Delete Library');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(loadingService.show).toHaveBeenCalledWith(expect.stringContaining(mockLibrary.name));
      expect(libraryService.deleteLibrary).toHaveBeenCalledWith(mockLibrary.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: 'Success',
        detail: 'Library was deleted'
      });
      expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
    });

    it('should show error message if deleteLibrary fails', () => {
      libraryService.deleteLibrary.mockReturnValue(throwError(() => ({})));
      const items = service.initializeLibraryMenuItems(mockLibrary);
      const del = items[0].items!.find(i => i.label === 'Delete Library');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'Failed to delete library'
      });
      expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
    });
  });

  describe('initializeShelfMenuItems', () => {
    it('should provide all menu items for a shelf', () => {
      const items = service.initializeShelfMenuItems(mockShelf);
      expect(items.length).toBe(1);
      const options = items[0].items!;
      expect(options.some(i => i.label === 'Edit Shelf')).toBe(true);
      expect(options.some(i => i.label === 'Delete Shelf')).toBe(true);
    });

    it('should call openShelfEditDialog when Edit Shelf is clicked', () => {
      const items = service.initializeShelfMenuItems(mockShelf);
      const edit = items[0].items!.find(i => i.label === 'Edit Shelf');
      edit!.command!({});
      expect(dialogLauncherService.openShelfEditDialog).toHaveBeenCalledWith(mockShelf.id);
    });

    it('should show confirmation dialog for Delete Shelf', () => {
      shelfService.deleteShelf.mockReturnValue(of({}));
      const items = service.initializeShelfMenuItems(mockShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Shelf');
      del!.command!({});
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      expect(confirmCall.message).toContain(mockShelf.name);
      expect(confirmCall.header).toBe('Confirmation');
    });

    it('should delete shelf, navigate, and show success message on accept', () => {
      shelfService.deleteShelf.mockReturnValue(of({}));
      const items = service.initializeShelfMenuItems(mockShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Shelf');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(shelfService.deleteShelf).toHaveBeenCalledWith(mockShelf.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: 'Success',
        detail: 'Shelf was deleted'
      });
    });

    it('should show error message if deleteShelf fails', () => {
      shelfService.deleteShelf.mockReturnValue(throwError(() => ({})));
      const items = service.initializeShelfMenuItems(mockShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Shelf');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'Failed to delete shelf'
      });
    });
  });

  describe('initializeMagicShelfMenuItems', () => {
    it('should provide all menu items for a magic shelf', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      const items = service.initializeMagicShelfMenuItems(mockMagicShelf);
      expect(items.length).toBe(1);
      const options = items[0].items!;
      expect(options.some(i => i.label === 'Edit Magic Shelf')).toBe(true);
      expect(options.some(i => i.label === 'Delete Magic Shelf')).toBe(true);
    });

    it('should call openMagicShelfEditDialog when Edit Magic Shelf is clicked', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      const items = service.initializeMagicShelfMenuItems(mockMagicShelf);
      const edit = items[0].items!.find(i => i.label === 'Edit Magic Shelf');
      edit!.command!({});
      expect(dialogLauncherService.openMagicShelfEditDialog).toHaveBeenCalledWith(mockMagicShelf.id);
    });

    it('should show confirmation dialog for Delete Magic Shelf', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      magicShelfService.deleteShelf.mockReturnValue(of({}));
      const items = service.initializeMagicShelfMenuItems(mockMagicShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Magic Shelf');
      del!.command!({});
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      expect(confirmCall.message).toContain(mockMagicShelf.name);
      expect(confirmCall.header).toBe('Confirmation');
    });

    it('should delete magic shelf, navigate, and show success message on accept', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      magicShelfService.deleteShelf.mockReturnValue(of({}));
      const items = service.initializeMagicShelfMenuItems(mockMagicShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Magic Shelf');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(magicShelfService.deleteShelf).toHaveBeenCalledWith(mockMagicShelf.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: 'Success',
        detail: 'Magic shelf was deleted'
      });
    });

    it('should show error message if deleteMagicShelf fails', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      magicShelfService.deleteShelf.mockReturnValue(throwError(() => ({})));
      const items = service.initializeMagicShelfMenuItems(mockMagicShelf);
      const del = items[0].items!.find(i => i.label === 'Delete Magic Shelf');
      del!.command!({});
      const confirmCall = confirmationService.confirm.mock.calls[0][0];
      confirmCall.accept();
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'Failed to delete shelf'
      });
    });

    it('should disable options for public shelf if not admin', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: false}});
      const publicMagicShelf = {...mockMagicShelf, isPublic: true};
      const items = service.initializeMagicShelfMenuItems(publicMagicShelf);
      const edit = items[0].items!.find(i => i.label === 'Edit Magic Shelf');
      const del = items[0].items!.find(i => i.label === 'Delete Magic Shelf');
      expect(edit!.disabled).toBe(true);
      expect(del!.disabled).toBe(true);
    });

    it('should not disable options for public shelf if admin', () => {
      userService.getCurrentUser.mockReturnValue({permissions: {admin: true}});
      const publicMagicShelf = {...mockMagicShelf, isPublic: true};
      const items = service.initializeMagicShelfMenuItems(publicMagicShelf);
      const edit = items[0].items!.find(i => i.label === 'Edit Magic Shelf');
      const del = items[0].items!.find(i => i.label === 'Delete Magic Shelf');
      expect(edit!.disabled).toBe(false);
      expect(del!.disabled).toBe(false);
    });
  });
});

