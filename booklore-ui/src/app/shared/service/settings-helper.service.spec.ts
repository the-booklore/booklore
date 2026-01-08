import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {of, throwError} from 'rxjs';
import {SettingsHelperService} from './settings-helper.service';
import {AppSettingsService} from './app-settings.service';
import {MessageService} from 'primeng/api';

describe('SettingsHelperService', () => {
  let service: SettingsHelperService;
  let appSettingsServiceMock: any;
  let messageServiceMock: any;

  beforeEach(() => {
    appSettingsServiceMock = {
      saveSettings: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        SettingsHelperService,
        {provide: AppSettingsService, useValue: appSettingsServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(SettingsHelperService));
  });

  it('should call saveSettings and show success message on success', () => {
    appSettingsServiceMock.saveSettings.mockReturnValue(of(void 0));
    service.saveSetting('theme', 'dark').subscribe(result => {
      expect(result).toBeUndefined();
      expect(appSettingsServiceMock.saveSettings).toHaveBeenCalledWith([{key: 'theme', newValue: 'dark'}]);
      expect(messageServiceMock.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: 'Settings Saved',
        detail: 'The settings were successfully saved!'
      });
    });
  });

  it('should call saveSettings and show error message on error', () => {
    appSettingsServiceMock.saveSettings.mockReturnValue(throwError(() => new Error('fail')));
    service.saveSetting('theme', 'light').subscribe({
      error: (err: any) => {
        expect(appSettingsServiceMock.saveSettings).toHaveBeenCalledWith([{key: 'theme', newValue: 'light'}]);
        expect(messageServiceMock.add).toHaveBeenCalledWith({
          severity: 'error',
          summary: 'Error',
          detail: 'There was an error saving the settings.'
        });
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should show custom message via showMessage', () => {
    service.showMessage('success', 'Yay', 'It worked!');
    expect(messageServiceMock.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Yay',
      detail: 'It worked!'
    });
  });
});
