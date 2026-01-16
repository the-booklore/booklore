import {Component, inject, OnInit} from '@angular/core';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingKey, AppSettings, MetadataPersistenceSettings, SaveToOriginalFileSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-metadata-persistence-settings-component',
  imports: [
    ToggleSwitch,
    FormsModule,
    Tooltip
  ],
  templateUrl: './metadata-persistence-settings-component.html',
  styleUrl: './metadata-persistence-settings-component.scss'
})
export class MetadataPersistenceSettingsComponent implements OnInit {

  metadataPersistence: MetadataPersistenceSettings = {
    saveToOriginalFile: {
      epub: {
        enabled: false,
        maxFileSizeInMb: 250
      },
      pdf: {
        enabled: false,
        maxFileSizeInMb: 250
      },
      cbx: {
        enabled: false,
        maxFileSizeInMb: 250
      }
    },
    convertCbrCb7ToCbz: false,
    moveFilesToLibraryPattern: false
  };

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.loadSettings();
  }

  onPersistenceToggle(key: keyof MetadataPersistenceSettings): void {
    if (key !== 'saveToOriginalFile') {
      this.metadataPersistence[key] = !this.metadataPersistence[key];
      this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
    }
  }

  onSaveToOriginalFileToggle(format: keyof SaveToOriginalFileSettings): void {
    this.metadataPersistence.saveToOriginalFile[format].enabled =
      !this.metadataPersistence.saveToOriginalFile[format].enabled;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onFilesizeChange(format: keyof SaveToOriginalFileSettings): void {
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  private loadSettings(): void {
    this.appSettings$.pipe(
      filter((settings): settings is AppSettings => !!settings),
      take(1)
    ).subscribe({
      next: (settings) => this.initializeSettings(settings),
      error: (error) => {
        console.error('Failed to load settings:', error);
        this.settingsHelper.showMessage('error', 'Error', 'Failed to load settings.');
      }
    });
  }

  private initializeSettings(settings: AppSettings): void {
    if (settings.metadataPersistenceSettings) {
      const persistenceSettings = settings.metadataPersistenceSettings;

      this.metadataPersistence = {
        ...persistenceSettings,
        saveToOriginalFile: {
          epub: {
            enabled: persistenceSettings.saveToOriginalFile?.epub?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.epub?.maxFileSizeInMb ?? 250
          },
          pdf: {
            enabled: persistenceSettings.saveToOriginalFile?.pdf?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.pdf?.maxFileSizeInMb ?? 250
          },
          cbx: {
            enabled: persistenceSettings.saveToOriginalFile?.cbx?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.cbx?.maxFileSizeInMb ?? 250
          }
        }
      };
    }
  }
}
