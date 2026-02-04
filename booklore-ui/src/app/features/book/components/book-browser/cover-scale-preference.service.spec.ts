import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {BehaviorSubject} from 'rxjs';

describe('CoverScalePreferenceService', () => {
  let service: CoverScalePreferenceService;
  let userService: any;
  let messageService: any;
  let localStorageService: any;
  let userStateSubject: BehaviorSubject<any>;

  beforeEach(() => {
    userStateSubject = new BehaviorSubject({
      loaded: true,
      user: {
        id: 1,
        userSettings: {
          entityViewPreferences: {
            global: { coverSize: 1.2 }
          }
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

    localStorageService = {
      set: vi.fn(),
      get: vi.fn().mockReturnValue(1.0)
    };

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        { provide: UserService, useValue: userService },
        { provide: MessageService, useValue: messageService },
        { provide: LocalStorageService, useValue: localStorageService }
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);
  });

  it('should initialize scale from user state', () => {
    expect(service.scaleFactor).toBe(1.2);
  });

  it('should update scale Factor and emit on scaleChange$', () => {
    const spy = vi.fn();
    service.scaleChange$.subscribe(spy);

    service.setScale(1.5);

    expect(service.scaleFactor).toBe(1.5);
    expect(spy).toHaveBeenCalledWith(1.5);
  });

  it('should save scale preference to backend and local storage with debounce', async () => {
    vi.useFakeTimers();
    service.setScale(1.8);
    vi.advanceTimersByTime(1100);

    expect(localStorageService.set).toHaveBeenCalled();
    expect(userService.updateUserSetting).toHaveBeenCalledWith(1, 'entityViewPreferences', expect.objectContaining({
      global: expect.objectContaining({ coverSize: 1.8 })
    }));
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    vi.useRealTimers();
  });

  it('should prevent infinite loop using isUpdating flag', async () => {
    vi.useFakeTimers();
    // 1. Simulate a backend sync (setting isUpdating = true)
    service.setScale(2.0);
    vi.advanceTimersByTime(1005); // Triggers debounce(1000)

    // Now isUpdating is true, and setTimeout(..., 100) is pending

    // 2. Simulate userState$ emission (e.g. backend echo)
    userStateSubject.next({
      loaded: true,
      user: {
        id: 1,
        userSettings: {
          entityViewPreferences: {
            global: { coverSize: 0.5 }
          }
        }
      }
    });

    // It should STILL be 2.0 because isUpdating is true
    expect(service.scaleFactor).toBe(2.0);

    // 3. Advance past the settle delay (200ms)
    vi.advanceTimersByTime(250);

    // Now isUpdating is false, new emissions should work
    userStateSubject.next({
      loaded: true,
      user: {
        id: 1,
        userSettings: {
          entityViewPreferences: {
            global: { coverSize: 0.8 }
          }
        }
      }
    });
    expect(service.scaleFactor).toBe(0.8);
    vi.useRealTimers();
  });

  it('should calculate current card size correctly', () => {
    service.setScale(1.0);
    expect(service.currentCardSize).toEqual({ width: 135, height: 220 });

    service.setScale(2.0);
    expect(service.currentCardSize).toEqual({ width: 270, height: 440 });
  });

  it('should handle OnDestroy and clean up subscriptions', () => {
    const destroySpy = vi.spyOn((service as any).destroy$, 'next');
    const completeSpy = vi.spyOn((service as any).destroy$, 'complete');

    service.ngOnDestroy();

    expect(destroySpy).toHaveBeenCalled();
    expect(completeSpy).toHaveBeenCalled();
  });

  it('should handle null user gracefully', () => {
    userService.getCurrentUser.mockReturnValue(null);
    
    // Should not throw
    expect(() => service.setScale(1.5)).not.toThrow();
  });

  it('should not persist if scale equals persisted value', async () => {
    vi.useFakeTimers();
    
    // Set scale to same as persisted (1.2 from initial state)
    service.setScale(1.2);
    vi.advanceTimersByTime(1100);
    
    // Should not call updateUserSetting since value is same
    expect(userService.updateUserSetting).not.toHaveBeenCalled();
    
    vi.useRealTimers();
  });

  it('should clear pending timeout on destroy', () => {
    vi.useFakeTimers();
    
    service.setScale(2.0);
    vi.advanceTimersByTime(1005); // Triggers save, starts 200ms timeout
    
    // Destroy before timeout completes
    service.ngOnDestroy();
    
    // Verify no errors when timeout would have fired
    expect(() => vi.advanceTimersByTime(300)).not.toThrow();
    
    vi.useRealTimers();
  });

  it('should handle rapid scale changes with debounce', async () => {
    vi.useFakeTimers();
    
    service.setScale(1.1);
    service.setScale(1.2);
    service.setScale(1.3);
    service.setScale(1.4);
    
    vi.advanceTimersByTime(1100);
    
    // Should only save once with final value
    expect(userService.updateUserSetting).toHaveBeenCalledTimes(1);
    expect(userService.updateUserSetting).toHaveBeenCalledWith(
      1, 
      'entityViewPreferences', 
      expect.objectContaining({
        global: expect.objectContaining({ coverSize: 1.4 })
      })
    );
    
    vi.useRealTimers();
  });
});
