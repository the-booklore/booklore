import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Button} from 'primeng/button';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {filter, takeUntil} from 'rxjs/operators';
import {ExternalDocLinkComponent} from '../../../../../shared/components/external-doc-link/external-doc-link.component';
import {UserService} from '../../../user-management/user.service';
import {HardcoverSyncSettingsService} from './hardcover-sync-settings.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  standalone: true,
  selector: 'app-hardcover-settings-component',
  imports: [
    FormsModule,
    InputText,
    ToggleSwitch,
    Button,
    ToastModule,
    ExternalDocLinkComponent,
    TranslocoDirective
  ],
  providers: [MessageService],
  templateUrl: './hardcover-settings-component.html',
  styleUrls: ['./hardcover-settings-component.scss']
})
export class HardcoverSettingsComponent implements OnInit, OnDestroy {
  private readonly messageService = inject(MessageService);
  private readonly hardcoverSyncSettingsService = inject(HardcoverSyncSettingsService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  hasPermission = false;
  hardcoverSyncEnabled = false;
  hardcoverApiKey = '';
  showHardcoverApiKey = false;

  ngOnInit() {
    let prevHasPermission = false;
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      const currHasPermission = (userState.user?.permissions.canSyncKoReader
        || userState.user?.permissions.canSyncKobo
        || userState.user?.permissions.admin) ?? false;
      this.hasPermission = currHasPermission;
      if (currHasPermission && !prevHasPermission) {
        this.loadHardcoverSettings();
      }
      prevHasPermission = currHasPermission;
    });
  }

  private loadHardcoverSettings() {
    this.hardcoverSyncSettingsService.getSettings().subscribe({
      next: settings => {
        this.hardcoverSyncEnabled = settings.hardcoverSyncEnabled ?? false;
        this.hardcoverApiKey = settings.hardcoverApiKey ?? '';
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsDevice.hardcover.loadError')
        });
      }
    });
  }

  toggleShowHardcoverApiKey() {
    this.showHardcoverApiKey = !this.showHardcoverApiKey;
  }

  onHardcoverSyncToggle() {
    const message = this.hardcoverSyncEnabled
      ? this.t.translate('settingsDevice.hardcover.syncEnabledMsg')
      : this.t.translate('settingsDevice.hardcover.syncDisabledMsg');
    this.updateHardcoverSettings(message);
  }

  onHardcoverApiKeyChange() {
    this.updateHardcoverSettings(this.t.translate('settingsDevice.hardcover.apiKeyUpdated'));
  }

  private updateHardcoverSettings(successMessage: string) {
    this.hardcoverSyncSettingsService.updateSettings({
      hardcoverSyncEnabled: this.hardcoverSyncEnabled,
      hardcoverApiKey: this.hardcoverApiKey
    }).subscribe({
      next: settings => {
        this.hardcoverSyncEnabled = settings.hardcoverSyncEnabled ?? false;
        this.hardcoverApiKey = settings.hardcoverApiKey ?? '';
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.hardcover.settingsUpdated'), detail: successMessage});
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsDevice.hardcover.updateFailed'),
          detail: this.t.translate('settingsDevice.hardcover.updateError')
        });
      }
    });
  }

  copyText(text: string, label: string = 'Text') {
    if (!text) {
      return;
    }
    navigator.clipboard.writeText(text).then(() => {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsDevice.copied'),
        detail: this.t.translate('settingsDevice.copiedDetail', {label})
      });
    }).catch(err => {
      console.error('Copy failed', err);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('settingsDevice.copyFailed'),
        detail: this.t.translate('settingsDevice.copyFailedDetail', {label})
      });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
