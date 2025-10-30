import {Component, inject, OnInit} from '@angular/core';
import {MetadataProviderSettingsComponent} from '../global-preferences/metadata-provider-settings/metadata-provider-settings.component';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../shared/service/settings-helper.service';
import {Observable} from 'rxjs';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MetadataMatchWeightsComponent} from '../global-preferences/metadata-match-weights/metadata-match-weights-component';
import {MetadataPersistenceSettingsComponent} from './metadata-persistence-settings/metadata-persistence-settings-component';
import {PublicReviewsSettingsComponent} from './public-reviews-settings/public-reviews-settings-component';

@Component({
  selector: 'app-metadata-settings-component',
  standalone: true,
  imports: [
    MetadataProviderSettingsComponent,
    ReactiveFormsModule,
    FormsModule,
    MetadataMatchWeightsComponent,
    ToggleSwitch,
    MetadataPersistenceSettingsComponent,
    PublicReviewsSettingsComponent
  ],
  templateUrl: './metadata-settings-component.html',
  styleUrl: './metadata-settings-component.scss'
})
export class MetadataSettingsComponent implements OnInit {

  currentMetadataOptions!: MetadataRefreshOptions;
  metadataDownloadOnBookdrop = true;

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.loadSettings();
  }

  onMetadataDownloadOnBookdropToggle(checked: boolean): void {
    this.metadataDownloadOnBookdrop = checked;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, checked);
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions): void {
    this.currentMetadataOptions = metadataRefreshOptions;
    this.settingsHelper.saveSetting(AppSettingKey.QUICK_BOOK_MATCH, metadataRefreshOptions);
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
    if (settings.defaultMetadataRefreshOptions) {
      this.currentMetadataOptions = settings.defaultMetadataRefreshOptions;
    }

    this.metadataDownloadOnBookdrop = settings.metadataDownloadOnBookdrop ?? true;
  }
}
