import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Tooltip} from 'primeng/tooltip';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule} from '@angular/forms';
import {UserService} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';

@Component({
  selector: 'app-meta-center-view-mode-component',
  imports: [
    Tooltip,
    RadioButton,
    FormsModule
  ],
  templateUrl: './meta-center-view-mode-component.html',
  styleUrl: './meta-center-view-mode-component.scss'
})
export class MetaCenterViewModeComponent implements OnInit, OnDestroy {
  viewMode: 'route' | 'dialog' = 'route';

  private userService = inject(UserService);
  private messageService = inject(MessageService);
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

  private savePreference(value: 'route' | 'dialog'): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.metadataCenterViewMode = value;
    this.userService.updateUserSetting(user.id, 'metadataCenterViewMode', value);

    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Updated',
      detail: 'Your metadata center view preference has been saved.',
      life: 1500,
    });
  }
}
