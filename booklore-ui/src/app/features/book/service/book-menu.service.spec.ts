import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {ConfirmationService, MessageService} from 'primeng/api';
import {BookService} from './book.service';
import {LoadingService} from '../../../core/services/loading.service';
import {User} from '../../settings/user-management/user.service';
import {ReadStatus} from '../model/book.model';
import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {APIException} from '../../../shared/models/api-exception.model';
import {BookMenuService} from './book-menu.service';

describe('BookMenuService', () => {
  let service: BookMenuService;
  let confirmationService: any;
  let messageService: any;
  let bookService: any;
  let loadingService: any;

  const mockUser: User = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    permissions: {
      canBulkAutoFetchMetadata: true,
      canBulkCustomFetchMetadata: true,
      canBulkEditMetadata: true,
      canBulkRegenerateCover: true,
      canBulkResetBookReadStatus: true,
      canBulkResetBookloreReadProgress: true,
      canBulkResetKoReaderReadProgress: true
    }
  } as User;

  const mockUserNoPermissions: User = {
    id: 2,
    username: 'limiteduser',
    email: 'limited@example.com',
    permissions: {
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canBulkResetBookReadStatus: false,
      canBulkResetBookloreReadProgress: false,
      canBulkResetKoReaderReadProgress: false
    }
  } as User;

  beforeEach(() => {
    confirmationService = {
      confirm: vi.fn()
    };

    messageService = {
      add: vi.fn()
    };

    bookService = {
      updateBookReadStatus: vi.fn(),
      resetProgress: vi.fn()
    };

    loadingService = {
      show: vi.fn().mockReturnValue('loader-id'),
      hide: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: BookService, useValue: bookService},
        {provide: LoadingService, useValue: loadingService}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);

    service = runInInjectionContext(
      injector,
      () => TestBed.inject(BookMenuService)
    );
  });

  describe('getMetadataMenuItems', () => {
    it('should return all metadata menu items when user has all permissions', () => {
      const autoFetch = vi.fn();
      const fetch = vi.fn();
      const bulkEdit = vi.fn();
      const multiEdit = vi.fn();
      const regenerate = vi.fn();
      const generateCustomCovers = vi.fn();

      const items = service.getMetadataMenuItems(
        autoFetch,
        fetch,
        bulkEdit,
        multiEdit,
        regenerate,
        generateCustomCovers,
        mockUser
      );

      expect(items).toHaveLength(6);
      expect(items[0].label).toBe('Auto Fetch Metadata');
      expect(items[0].icon).toBe('pi pi-bolt');
      expect(items[1].label).toBe('Custom Fetch Metadata');
      expect(items[1].icon).toBe('pi pi-sync');
      expect(items[2].label).toBe('Bulk Metadata Editor');
      expect(items[2].icon).toBe('pi pi-table');
      expect(items[3].label).toBe('Multi-Book Metadata Editor');
      expect(items[3].icon).toBe('pi pi-clone');
      expect(items[4].label).toBe('Regenerate Covers');
      expect(items[4].icon).toBe('pi pi-image');
      expect(items[5].label).toBe('Generate Custom Covers');
      expect(items[5].icon).toBe('pi pi-palette');
    });

    it('should return empty array when user has no permissions', () => {
      const items = service.getMetadataMenuItems(
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        mockUserNoPermissions
      );

      expect(items).toHaveLength(0);
    });

    it('should return empty array when user is null', () => {
      const items = service.getMetadataMenuItems(
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        null
      );

      expect(items).toHaveLength(0);
    });

    it('should call command functions when menu items are clicked', () => {
      const autoFetch = vi.fn();
      const fetch = vi.fn();
      const bulkEdit = vi.fn();
      const multiEdit = vi.fn();
      const regenerate = vi.fn();
      const generateCustomCovers = vi.fn();

      const items = service.getMetadataMenuItems(
        autoFetch,
        fetch,
        bulkEdit,
        multiEdit,
        regenerate,
        generateCustomCovers,
        mockUser
      );

      items[0].command!({} as any);
      expect(autoFetch).toHaveBeenCalledOnce();

      items[1].command!({} as any);
      expect(fetch).toHaveBeenCalledOnce();

      items[2].command!({} as any);
      expect(bulkEdit).toHaveBeenCalledOnce();

      items[3].command!({} as any);
      expect(multiEdit).toHaveBeenCalledOnce();

      items[4].command!({} as any);
      expect(regenerate).toHaveBeenCalledOnce();

      items[5].command!({} as any);
      expect(generateCustomCovers).toHaveBeenCalledOnce();
    });

    it('should return only items for granted permissions', () => {
      const partialUser: User = {
        ...mockUser,
        permissions: {
          ...mockUser.permissions,
          canBulkAutoFetchMetadata: true,
          canBulkCustomFetchMetadata: false,
          canBulkEditMetadata: true,
          canBulkRegenerateCover: false
        }
      } as User;

      const items = service.getMetadataMenuItems(
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        vi.fn(),
        partialUser
      );

      expect(items).toHaveLength(3);
      expect(items[0].label).toBe('Auto Fetch Metadata');
      expect(items[1].label).toBe('Bulk Metadata Editor');
      expect(items[2].label).toBe('Multi-Book Metadata Editor');
    });
  });

  describe('getBulkReadActionsMenu', () => {
    it('should return all read action items when user has all permissions', () => {
      const selectedBooks = new Set([1, 2, 3]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);

      expect(items).toHaveLength(3);
      expect(items[0].label).toBe('Update Read Status');
      expect(items[0].icon).toBe('pi pi-book');
      expect(items[0].items).toBeDefined();
      expect(items[0].items!.length).toBeGreaterThan(0);
      expect(items[1].label).toBe('Reset Booklore Progress');
      expect(items[1].icon).toBe('pi pi-undo');
      expect(items[2].label).toBe('Reset KOReader Progress');
      expect(items[2].icon).toBe('pi pi-undo');
    });

    it('should return empty array when user has no permissions', () => {
      const selectedBooks = new Set([1, 2, 3]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUserNoPermissions);

      expect(items).toHaveLength(0);
    });

    it('should return empty array when user is null', () => {
      const selectedBooks = new Set([1, 2, 3]);
      const items = service.getBulkReadActionsMenu(selectedBooks, null);

      expect(items).toHaveLength(0);
    });

    it('should show confirmation dialog when updating read status', () => {
      const selectedBooks = new Set([1, 2, 3]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);

      const readStatusItems = items[0].items!;
      readStatusItems[0].command!({} as any);

      expect(confirmationService.confirm).toHaveBeenCalledOnce();
      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      expect(confirmCall.message).toContain('3 book(s)');
      expect(confirmCall.header).toBe('Confirm Read Status Update');
    });

    it('should update read status on confirmation accept', () => {
      const selectedBooks = new Set([1, 2, 3]);
      vi.mocked(bookService.updateBookReadStatus).mockReturnValue(of([]));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      const readStatusItems = items[0].items!;

      const readItem = readStatusItems.find(item => item.label === 'Read');
      expect(readItem).toBeDefined();

      readItem!.command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(loadingService.show).toHaveBeenCalledWith('Updating read status for 3 book(s)...');
      expect(bookService.updateBookReadStatus).toHaveBeenCalledWith([1, 2, 3], ReadStatus.READ);
      expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: 'Read Status Updated',
        detail: 'Marked as "Read"',
        life: 2000
      });
    });

    it('should show error message when read status update fails', () => {
      const selectedBooks = new Set([1, 2]);
      const errorResponse = new HttpErrorResponse({
        error: {message: 'Update failed'} as APIException,
        status: 500
      });
      vi.mocked(bookService.updateBookReadStatus).mockReturnValue(throwError(() => errorResponse));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      const readStatusItems = items[0].items!;

      readStatusItems[0].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Update Failed',
        detail: 'Update failed',
        life: 3000
      });
    });

    it('should show confirmation dialog when resetting Booklore progress', () => {
      const selectedBooks = new Set([1, 2, 3]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);

      items[1].command!({} as any);

      expect(confirmationService.confirm).toHaveBeenCalledOnce();
      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      expect(confirmCall.message).toContain('3 book(s)');
      expect(confirmCall.header).toBe('Confirm Reset');
    });

    it('should reset Booklore progress on confirmation accept', () => {
      const selectedBooks = new Set([5, 6]);
      vi.mocked(bookService.resetProgress).mockReturnValue(of([]));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      items[1].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(loadingService.show).toHaveBeenCalledWith('Resetting Booklore progress for 2 book(s)...');
      expect(bookService.resetProgress).toHaveBeenCalledWith([5, 6], ResetProgressTypes.BOOKLORE);
      expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: 'Progress Reset',
        detail: 'Booklore reading progress has been reset.',
        life: 1500
      });
    });

    it('should show error message when Booklore progress reset fails', () => {
      const selectedBooks = new Set([1]);
      const errorResponse = new HttpErrorResponse({
        error: {message: 'Reset failed'} as APIException,
        status: 500
      });
      vi.mocked(bookService.resetProgress).mockReturnValue(throwError(() => errorResponse));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      items[1].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'Reset failed',
        life: 3000
      });
    });

    it('should show confirmation dialog when resetting KOReader progress', () => {
      const selectedBooks = new Set([7, 8]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);

      items[2].command!({} as any);

      expect(confirmationService.confirm).toHaveBeenCalledOnce();
      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      expect(confirmCall.message).toContain('2 book(s)');
      expect(confirmCall.message).toContain('KOReader');
      expect(confirmCall.header).toBe('Confirm Reset');
    });

    it('should reset KOReader progress on confirmation accept', () => {
      const selectedBooks = new Set([9]);
      vi.mocked(bookService.resetProgress).mockReturnValue(of([]));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      items[2].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(loadingService.show).toHaveBeenCalledWith('Resetting KOReader progress for 1 book(s)...');
      expect(bookService.resetProgress).toHaveBeenCalledWith([9], ResetProgressTypes.KOREADER);
      expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: 'Progress Reset',
        detail: 'KOReader reading progress has been reset.',
        life: 1500
      });
    });

    it('should show error message when KOReader progress reset fails', () => {
      const selectedBooks = new Set([10]);
      const errorResponse = new HttpErrorResponse({
        error: {message: 'KOReader reset failed'} as APIException,
        status: 500
      });
      vi.mocked(bookService.resetProgress).mockReturnValue(throwError(() => errorResponse));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      items[2].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Failed',
        detail: 'KOReader reset failed',
        life: 3000
      });
    });

    it('should handle API error without message gracefully', () => {
      const selectedBooks = new Set([1]);
      const errorResponse = new HttpErrorResponse({
        error: {} as APIException,
        status: 500
      });
      vi.mocked(bookService.updateBookReadStatus).mockReturnValue(throwError(() => errorResponse));

      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);
      const readStatusItems = items[0].items!;

      readStatusItems[0].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.accept!();

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Update Failed',
        detail: 'Could not update read status.',
        life: 3000
      });
    });

    it('should return only items for granted read permissions', () => {
      const partialUser: User = {
        ...mockUser,
        permissions: {
          ...mockUser.permissions,
          canBulkResetBookReadStatus: true,
          canBulkResetBookloreReadProgress: false,
          canBulkResetKoReaderReadProgress: true
        }
      } as User;

      const selectedBooks = new Set([1, 2]);
      const items = service.getBulkReadActionsMenu(selectedBooks, partialUser);

      expect(items).toHaveLength(2);
      expect(items[0].label).toBe('Update Read Status');
      expect(items[1].label).toBe('Reset KOReader Progress');
    });

    it('should not call bookService when confirmation is rejected', () => {
      const selectedBooks = new Set([1, 2]);
      const items = service.getBulkReadActionsMenu(selectedBooks, mockUser);

      items[1].command!({} as any);

      const confirmCall = vi.mocked(confirmationService.confirm).mock.calls[0][0];
      confirmCall.reject?.();

      expect(bookService.resetProgress).not.toHaveBeenCalled();
      expect(loadingService.show).not.toHaveBeenCalled();
    });
  });
});

