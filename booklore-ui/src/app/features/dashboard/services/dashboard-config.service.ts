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
  private configSubject = new BehaviorSubject<DashboardConfig>(DEFAULT_DASHBOARD_CONFIG);

  public config$: Observable<DashboardConfig> = this.configSubject.asObservable();

  constructor(private userService: UserService, private magicShelfService: MagicShelfService) {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1)
      )
      .subscribe(userState => {
        const dashboardConfig = userState.user?.userSettings?.dashboardConfig as DashboardConfig;
        if (dashboardConfig) {
          this.configSubject.next(dashboardConfig);
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
        const user = this.userService.getCurrentUser();
        if (user) {
          this.userService.updateUserSetting(user.id, 'dashboardConfig', currentConfig);
        }
      }
    });
  }

  saveConfig(config: DashboardConfig): void {
    this.configSubject.next(config);

    const user = this.userService.getCurrentUser();
    if (user) {
      this.userService.updateUserSetting(user.id, 'dashboardConfig', config);
    }
  }

  resetToDefault(): void {
    this.saveConfig(DEFAULT_DASHBOARD_CONFIG);
  }
}
