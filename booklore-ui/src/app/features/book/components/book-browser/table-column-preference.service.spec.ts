import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {TableColumnPreferenceService} from './table-column-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {BehaviorSubject} from 'rxjs';

describe('TableColumnPreferenceService', () => {
  let service: TableColumnPreferenceService;
  let userService: any;
  let messageService: any;
  let userStateSubject: BehaviorSubject<any>;

  beforeEach(() => {
    userStateSubject = new BehaviorSubject({
      loaded: true,
      user: {
        id: 1,
        userSettings: {
          tableColumnPreference: [
            { field: 'title', visible: true, order: 0, width: '100px' },
            { field: 'authors', visible: false, order: 1, width: '150px' }
          ]
        }
      }
    });

    userService = {
      userState$: userStateSubject.asObservable(),
      getCurrentUser: vi.fn().mockReturnValue(userStateSubject.value.user),
      updateUserSetting: vi.fn()
    };

    messageService = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        TableColumnPreferenceService,
        { provide: UserService, useValue: userService },
        { provide: MessageService, useValue: messageService }
      ]
    });

    service = TestBed.inject(TableColumnPreferenceService);
    service.initPreferences(userStateSubject.value.user.userSettings.tableColumnPreference);
  });

  it('should initialize preferences correctly', () => {
    expect(service.preferences.length).toBeGreaterThan(0);
    expect(service.preferences.find(p => p.field === 'title')?.visible).toBe(true);
  });

  it('should memoize visibleColumns and only recalculate when needed', () => {
    service.initPreferences([]);
    
    const visible1 = service.visibleColumns;
    const visible2 = service.visibleColumns;
    
    // Check reference equality for memoization
    expect(visible1).toBe(visible2);
    
    // Update preferences (should invalidate cache)
    service.saveVisibleColumns([{ field: 'title' }]);
    const visible3 = service.visibleColumns;
    
    expect(visible3).not.toBe(visible1);
    expect(visible3.length).toBe(1);
    expect(visible3[0].field).toBe('title');
  });

  it('should look up headers efficiently using pre-computed Map', () => {
    // Check internal preferences first
    expect(service.preferences.length).toBeGreaterThan(0);
    const titlePref = service.preferences.find(p => p.field === 'title');
    expect(titlePref).toBeDefined();
    expect(titlePref?.visible).toBe(true);

    // Header check
    const visible = service.visibleColumns;
    expect(visible.length).toBeGreaterThan(0);
    
    const titleCol = visible.find(c => c.field === 'title');
    expect(titleCol).toBeDefined();
    expect(titleCol?.header).toBe('Title');
    
    service.saveVisibleColumns([{ field: 'isbn' }]);
    expect(service.visibleColumns[0].header).toBe('ISBN');
  });

  it('should save column widths and update preferences', () => {
    service.initPreferences([{ field: 'title', visible: true, order: 0, width: '100px' }]);
    
    service.saveColumnWidths([{ field: 'title', width: '250px' }]);
    
    const titlePref = service.preferences.find(p => p.field === 'title');
    expect(titlePref?.width).toBe('250px');
    expect(userService.updateUserSetting).toHaveBeenCalled();
  });

  it('should reset preferences to default', () => {
    service.resetPreferences();
    
    expect(service.preferences.length).toBeGreaterThan(1);
    expect(service.preferences.every(p => p.visible)).toBe(true);
    expect(userService.updateUserSetting).toHaveBeenCalled();
  });

  it('should handle empty saved preferences', () => {
    service.initPreferences([]);
    
    expect(service.preferences.length).toBeGreaterThan(0);
    expect(service.visibleColumns.length).toBeGreaterThan(0);
  });

  it('should preserve order when toggling visibility', () => {
    service.initPreferences([
      { field: 'title', visible: true, order: 0 },
      { field: 'authors', visible: true, order: 1 },
      { field: 'publisher', visible: true, order: 2 }
    ]);
    
    // Remove authors, then add back
    service.saveVisibleColumns([{ field: 'title' }, { field: 'publisher' }]);
    service.saveVisibleColumns([{ field: 'title' }, { field: 'publisher' }, { field: 'authors' }]);
    
    const authorsPref = service.preferences.find(p => p.field === 'authors');
    expect(authorsPref).toBeDefined();
    expect(authorsPref?.order).toBe(2);
  });

  it('should handle null user gracefully when saving', () => {
    userService.getCurrentUser.mockReturnValue(null);
    
    // Should not throw
    expect(() => service.saveColumnWidths([{ field: 'title', width: '200px' }])).not.toThrow();
  });
});
