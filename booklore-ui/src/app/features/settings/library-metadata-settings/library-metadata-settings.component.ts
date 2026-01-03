import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {AccordionModule} from 'primeng/accordion';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';

import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {CustomFieldType, LibraryCustomField} from '../../book/model/library-custom-field.model';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {MetadataAdvancedFetchOptionsComponent} from '../../metadata/component/metadata-options-dialog/metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';

@Component({
  selector: 'app-library-metadata-settings-component',
  standalone: true,
  imports: [CommonModule, FormsModule, MetadataAdvancedFetchOptionsComponent, AccordionModule, ExternalDocLinkComponent, Button, InputText, Select],
  templateUrl: './library-metadata-settings.component.html',
  styleUrls: ['./library-metadata-settings.component.scss']
})
export class LibraryMetadataSettingsComponent implements OnInit {
  private libraryService = inject(LibraryService);
  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);

  libraries$: Observable<Library[]> = this.libraryService.libraryState$.pipe(
    map(state => state.libraries || [])
  );

  defaultMetadataOptions: MetadataRefreshOptions = this.getDefaultMetadataOptions();
  libraryMetadataOptions: Record<number, MetadataRefreshOptions> = {};

  customFieldsByLibraryId: Record<number, LibraryCustomField[]> = {};
  newCustomFieldByLibraryId: Record<number, { name: string; fieldType: CustomFieldType; defaultValue?: string | null }> = {};

  customFieldTypeOptions: { label: string; value: CustomFieldType }[] = [
    {label: 'String', value: 'STRING'},
    {label: 'Number', value: 'NUMBER'},
    {label: 'Date', value: 'DATE'},
  ];

  ngOnInit() {
    this.appSettingsService.appSettings$.subscribe(appSettings => {
      if (appSettings) {
        this.defaultMetadataOptions = appSettings.defaultMetadataRefreshOptions;
        this.initializeLibraryOptions(appSettings);
        this.updateLibraryOptionsFromSettings();
      }
    });

    this.libraries$.subscribe(libraries => {
      libraries.forEach(library => {
        if (library.id && !this.libraryMetadataOptions[library.id]) {
          const libraryOptions = this.getLibrarySpecificOptions(library.id);
          if (libraryOptions) {
            this.libraryMetadataOptions[library.id] = libraryOptions;
          }
        }

        if (library.id && !this.customFieldsByLibraryId[library.id]) {
          this.loadCustomFields(library.id);
        }
      });
    });
  }

  loadCustomFields(libraryId: number) {
    this.libraryService.getCustomFields(libraryId).subscribe({
      next: (fields) => {
        this.customFieldsByLibraryId[libraryId] = fields;
        if (!this.newCustomFieldByLibraryId[libraryId]) {
          this.newCustomFieldByLibraryId[libraryId] = {name: '', fieldType: 'STRING', defaultValue: null};
        }
      },
      error: () => {
        this.customFieldsByLibraryId[libraryId] = [];
        if (!this.newCustomFieldByLibraryId[libraryId]) {
          this.newCustomFieldByLibraryId[libraryId] = {name: '', fieldType: 'STRING', defaultValue: null};
        }
      }
    });
  }

  addCustomField(libraryId: number) {
    const draft = this.newCustomFieldByLibraryId[libraryId];
    if (!draft?.name?.trim()) {
      this.showMessage('error', 'Invalid', 'Custom field name is required.');
      return;
    }

    this.libraryService.createCustomField(libraryId, {
      name: draft.name.trim(),
      fieldType: draft.fieldType,
      defaultValue: (draft.defaultValue ?? null)
    }).subscribe({
      next: () => {
        this.showMessage('success', 'Created', 'Custom field created.');
        this.newCustomFieldByLibraryId[libraryId] = {name: '', fieldType: 'STRING', defaultValue: null};
        this.loadCustomFields(libraryId);
      },
      error: (error) => {
        this.showMessage('error', 'Create Failed', error?.error?.message || 'Failed to create custom field.');
      }
    });
  }

  deleteCustomField(libraryId: number, customFieldId: number) {
    this.libraryService.deleteCustomField(libraryId, customFieldId).subscribe({
      next: () => {
        this.showMessage('success', 'Deleted', 'Custom field deleted.');
        this.loadCustomFields(libraryId);
      },
      error: (error) => {
        this.showMessage('error', 'Delete Failed', error?.error?.message || 'Failed to delete custom field.');
      }
    });
  }

  onDefaultMetadataOptionsSubmitted(options: MetadataRefreshOptions) {
    this.defaultMetadataOptions = options;
    this.saveDefaultMetadataOptions(options);
  }

  onLibraryMetadataOptionsSubmitted(libraryId: number, options: MetadataRefreshOptions) {
    this.libraryMetadataOptions[libraryId] = {...options, libraryId};
    this.saveLibraryMetadataOptions();
  }

  hasLibraryOverride(libraryId: number): boolean {
    return libraryId in this.libraryMetadataOptions;
  }

  getLibraryOptions(libraryId: number): MetadataRefreshOptions {
    return this.libraryMetadataOptions[libraryId] || {...this.defaultMetadataOptions, libraryId};
  }

  trackByLibrary(index: number, library: Library): number | undefined {
    return library.id;
  }

  private saveDefaultMetadataOptions(options: MetadataRefreshOptions) {
    const settingsToSave = [
      {
        key: AppSettingKey.QUICK_BOOK_MATCH,
        newValue: options
      }
    ];

    this.appSettingsService.saveSettings(settingsToSave).subscribe({
      next: () => {
        this.showMessage('success', 'Settings Saved', 'Default metadata options have been saved successfully.');
        this.updateLibrariesUsingDefaults();
      },
      error: (error) => {
        console.error('Error saving default metadata options:', error);
        this.showMessage('error', 'Save Failed', 'Failed to save default metadata options. Please try again.');
      }
    });
  }

  private saveLibraryMetadataOptions() {
    const libraryOptionsArray = Object.values(this.libraryMetadataOptions).filter(option =>
      option.libraryId !== null && option.libraryId !== undefined
    );

    const settingsToSave = [
      {
        key: AppSettingKey.LIBRARY_METADATA_REFRESH_OPTIONS,
        newValue: libraryOptionsArray
      }
    ];

    this.appSettingsService.saveSettings(settingsToSave).subscribe({
      next: () => {
        this.showMessage('success', 'Settings Saved', 'Library metadata options have been saved successfully.');
      },
      error: (error) => {
        console.error('Error saving library metadata options:', error);
        this.showMessage('error', 'Save Failed', 'Failed to save library metadata options. Please try again.');
      }
    });
  }

  private updateLibrariesUsingDefaults() {
    Object.keys(this.libraryMetadataOptions).forEach(libraryIdStr => {
      const libraryId = parseInt(libraryIdStr, 10);
      if (!this.hasLibrarySpecificOptionsInSettings(libraryId)) {
        delete this.libraryMetadataOptions[libraryId];
      }
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string) {
    this.messageService.add({
      severity,
      summary,
      detail,
      life: 5000
    });
  }

  private initializeLibraryOptions(appSettings: AppSettings) {
    if (appSettings?.libraryMetadataRefreshOptions) {
      appSettings.libraryMetadataRefreshOptions.forEach(option => {
        if (option.libraryId) {
          this.libraryMetadataOptions[option.libraryId] = option;
        }
      });
    }
  }

  private updateLibraryOptionsFromSettings() {
    Object.keys(this.libraryMetadataOptions).forEach(libraryIdStr => {
      const libraryId = parseInt(libraryIdStr, 10);
      if (!this.hasLibrarySpecificOptions(libraryId)) {
        this.libraryMetadataOptions[libraryId] = {...this.defaultMetadataOptions};
      }
    });
  }

  private hasLibrarySpecificOptions(libraryId: number): boolean {
    return libraryId in this.libraryMetadataOptions;
  }

  private hasLibrarySpecificOptionsInSettings(libraryId: number): boolean {
    let hasOptions = false;
    this.appSettingsService.appSettings$.subscribe(settings => {
      hasOptions = settings?.libraryMetadataRefreshOptions?.some(
        option => option.libraryId === libraryId
      ) || false;
    }).unsubscribe();

    return hasOptions;
  }

  private getLibrarySpecificOptions(libraryId: number): MetadataRefreshOptions | null {
    let libraryOptions: MetadataRefreshOptions | null = null;
    this.appSettingsService.appSettings$.subscribe(settings => {
      libraryOptions = settings?.libraryMetadataRefreshOptions?.find(
        option => option.libraryId === libraryId
      ) || null;
    }).unsubscribe();

    return libraryOptions;
  }

  private getDefaultMetadataOptions(): MetadataRefreshOptions {
    return {
      libraryId: null,
      refreshCovers: false,
      mergeCategories: false,
      reviewBeforeApply: false,
      fieldOptions: {
        title: {p1: null, p2: null, p3: null, p4: null},
        subtitle: {p1: null, p2: null, p3: null, p4: null},
        description: {p1: null, p2: null, p3: null, p4: null},
        authors: {p1: null, p2: null, p3: null, p4: null},
        publisher: {p1: null, p2: null, p3: null, p4: null},
        publishedDate: {p1: null, p2: null, p3: null, p4: null},
        seriesName: {p1: null, p2: null, p3: null, p4: null},
        seriesNumber: {p1: null, p2: null, p3: null, p4: null},
        seriesTotal: {p1: null, p2: null, p3: null, p4: null},
        isbn13: {p1: null, p2: null, p3: null, p4: null},
        isbn10: {p1: null, p2: null, p3: null, p4: null},
        language: {p1: null, p2: null, p3: null, p4: null},
        categories: {p1: null, p2: null, p3: null, p4: null},
        cover: {p1: null, p2: null, p3: null, p4: null},
        pageCount: {p1: null, p2: null, p3: null, p4: null},
        asin: {p1: null, p2: null, p3: null, p4: null},
        goodreadsId: {p1: null, p2: null, p3: null, p4: null},
        comicvineId: {p1: null, p2: null, p3: null, p4: null},
        hardcoverId: {p1: null, p2: null, p3: null, p4: null},
        googleId: {p1: null, p2: null, p3: null, p4: null},
        amazonRating: {p1: null, p2: null, p3: null, p4: null},
        amazonReviewCount: {p1: null, p2: null, p3: null, p4: null},
        goodreadsRating: {p1: null, p2: null, p3: null, p4: null},
        goodreadsReviewCount: {p1: null, p2: null, p3: null, p4: null},
        hardcoverRating: {p1: null, p2: null, p3: null, p4: null},
        hardcoverReviewCount: {p1: null, p2: null, p3: null, p4: null},
        lubimyczytacId: {p1: null, p2: null, p3: null, p4: null},
        lubimyczytacRating: {p1: null, p2: null, p3: null, p4: null},
        moods: {p1: null, p2: null, p3: null, p4: null},
        tags: {p1: null, p2: null, p3: null, p4: null}
      }
    };
  }
}
