import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {UserService} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-meta-center-view-mode-component',
  imports: [
    FormsModule,
    TranslocoDirective
  ],
  templateUrl: './meta-center-view-mode-component.html',
  styleUrl: './meta-center-view-mode-component.scss'
})
export class MetaCenterViewModeComponent implements OnInit, OnDestroy {
  viewMode: 'route' | 'dialog' = 'route';
  seriesViewMode: boolean = false;

  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      const preference = userState.user?.userSettings?.metadataCenterViewMode;
      if (preference === 'dialog' || preference === 'route') {
        this.viewMode = preference;
      }
      const seriesPref = userState.user?.userSettings?.enableSeriesView;
      if (typeof seriesPref === 'boolean') {
        this.seriesViewMode = seriesPref;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onViewModeChange(value: 'route' | 'dialog'): void {
    this.viewMode = value;
    this.savePreference(value);
  }

  onSeriesViewModeChange(value: boolean): void {
    this.seriesViewMode = value;
    this.saveSeriesViewPreference(value);
  }

  private savePreference(value: 'route' | 'dialog'): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.metadataCenterViewMode = value;
    this.userService.updateUserSetting(user.id, 'metadataCenterViewMode', value);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.metaCenter.prefsUpdated'),
      detail: this.t.translate('settingsView.metaCenter.metaCenterSaved'),
      life: 1500,
    });
  }

  private saveSeriesViewPreference(value: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.enableSeriesView = value;
    this.userService.updateUserSetting(user.id, 'enableSeriesView', value);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.metaCenter.prefsUpdated'),
      detail: this.t.translate('settingsView.metaCenter.seriesViewSaved'),
      life: 1500,
    });
  }
}
