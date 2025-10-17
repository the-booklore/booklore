import {Injectable, inject} from '@angular/core';
import {AppSettingsService} from './app-settings.service';
import {MessageService} from 'primeng/api';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SettingsHelperService {

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);

  saveSetting(key: string, value: unknown): Observable<void> {
    const observable = this.appSettingsService.saveSettings([{key, newValue: value}]);

    observable.subscribe({
      next: () => this.showSuccessMessage(),
      error: (error) => {
        console.error('Failed to save setting:', error);
        this.showErrorMessage();
      }
    });

    return observable;
  }

  saveMultipleSettings(settings: { key: string, newValue: unknown }[]): Observable<void> {
    const observable = this.appSettingsService.saveSettings(settings);
    observable.subscribe({
      next: () => this.showSuccessMessage(),
      error: (error) => {
        console.error('Failed to save settings:', error);
        this.showErrorMessage();
      }
    });
    return observable;
  }

  private showSuccessMessage(): void {
    this.messageService.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: 'The settings were successfully saved!'
    });
  }

  private showErrorMessage(): void {
    this.messageService.add({
      severity: 'error',
      summary: 'Error',
      detail: 'There was an error saving the settings.'
    });
  }

  showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}

