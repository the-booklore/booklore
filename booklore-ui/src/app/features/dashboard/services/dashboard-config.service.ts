import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {DashboardConfig, DEFAULT_DASHBOARD_CONFIG} from '../models/dashboard-config.model';
import {UserService} from '../../settings/user-management/user.service';
import {filter, take} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class DashboardConfigService {
  private configSubject = new BehaviorSubject<DashboardConfig>(DEFAULT_DASHBOARD_CONFIG);

  public config$: Observable<DashboardConfig> = this.configSubject.asObservable();

  constructor(private userService: UserService) {
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
