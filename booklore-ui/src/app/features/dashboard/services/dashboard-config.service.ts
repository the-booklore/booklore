import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {DashboardConfig, DEFAULT_DASHBOARD_CONFIG, ScrollerType} from '../models/dashboard-config.model';
import {UserService} from '../../settings/user-management/user.service';
import {filter, take} from 'rxjs/operators';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';

@Injectable({
  providedIn: 'root'
})
export class DashboardConfigService {
  private static readonly STORAGE_KEY = 'booklore_dashboard_config';
  private configSubject = new BehaviorSubject<DashboardConfig>(this.loadFromLocalStorage());

  public config$: Observable<DashboardConfig> = this.configSubject.asObservable();

  constructor(private userService: UserService, private magicShelfService: MagicShelfService) {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1)
      )
      .subscribe(userState => {
        const backendConfig = userState.user?.userSettings?.dashboardConfig as DashboardConfig;
        if (backendConfig) {
          // Merge backend config with localStorage config to preserve fields that backend might strip
          const localConfig = this.configSubject.value;
          const mergedConfig = this.mergeConfigs(localConfig, backendConfig);
          this.configSubject.next(mergedConfig);
          this.saveToLocalStorage(mergedConfig);
        }
      });

    this.magicShelfService.shelvesState$.subscribe(state => {
      const currentConfig = this.configSubject.value;
      let updated = false;

      currentConfig.scrollers.forEach(scroller => {
        if (scroller.type === ScrollerType.MAGIC_SHELF && scroller.magicShelfId) {
          const shelf = state.shelves?.find(s => s.id === scroller.magicShelfId);
          if (shelf && scroller.title !== shelf.name) {
            scroller.title = shelf.name;
            updated = true;
          }
        }
      });

      if (updated) {
        this.configSubject.next({...currentConfig});
        this.saveToLocalStorage(currentConfig);
        const user = this.userService.getCurrentUser();
        if (user) {
          this.userService.updateUserSetting(user.id, 'dashboardConfig', currentConfig);
        }
      }
    });
  }

  saveConfig(config: DashboardConfig): void {
    this.configSubject.next(config);
    this.saveToLocalStorage(config);

    const user = this.userService.getCurrentUser();
    if (user) {
      this.userService.updateUserSetting(user.id, 'dashboardConfig', config);
    }
  }

  resetToDefault(): void {
    this.saveConfig(DEFAULT_DASHBOARD_CONFIG);
  }

  private loadFromLocalStorage(): DashboardConfig {
    try {
      const stored = localStorage.getItem(DashboardConfigService.STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as DashboardConfig;
        // Validate that it has the expected structure
        if (parsed && Array.isArray(parsed.scrollers)) {
          return parsed;
        }
      }
    } catch (error) {
      console.warn('Failed to load dashboard config from localStorage:', error);
    }
    return DEFAULT_DASHBOARD_CONFIG;
  }

  private saveToLocalStorage(config: DashboardConfig): void {
    try {
      localStorage.setItem(DashboardConfigService.STORAGE_KEY, JSON.stringify(config));
    } catch (error) {
      console.warn('Failed to save dashboard config to localStorage:', error);
    }
  }

  private mergeConfigs(localConfig: DashboardConfig, backendConfig: DashboardConfig): DashboardConfig {
    // Use backend config as base, but preserve fields from localStorage that might be stripped by backend
    const mergedScrollers = backendConfig.scrollers.map(backendScroller => {
      const localScroller = localConfig.scrollers.find(s => s.id === backendScroller.id);
      if (!localScroller) {
        return backendScroller;
      }

      // Merge scroller configs, preferring backend values but keeping localStorage fields if backend is missing them
      return {
        ...backendScroller,
        // Preserve these fields from localStorage if backend doesn't have them
        sortField: backendScroller.sortField ?? localScroller.sortField,
        sortDirection: backendScroller.sortDirection ?? localScroller.sortDirection,
        upNextShowFirstUnread: backendScroller.upNextShowFirstUnread ?? localScroller.upNextShowFirstUnread,
        readAgainSortByFinished: backendScroller.readAgainSortByFinished ?? localScroller.readAgainSortByFinished
      };
    });

    return { scrollers: mergedScrollers };
  }
}
