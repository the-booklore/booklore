import {
  Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, SimpleChanges
} from '@angular/core';
import {Select, SelectChangeEvent} from 'primeng/select';
import {FormsModule} from '@angular/forms';

import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {
  FieldOptions,
  FieldProvider,
  MetadataRefreshOptions
} from '../../model/request/metadata-refresh-options.model';
import {Tooltip} from 'primeng/tooltip';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {filter, take} from 'rxjs/operators';

@Component({
  selector: 'app-metadata-advanced-fetch-options',
  templateUrl: './metadata-advanced-fetch-options.component.html',
  imports: [Select, FormsModule, Checkbox, Button, Tooltip],
  styleUrl: './metadata-advanced-fetch-options.component.scss',
  standalone: true
})
export class MetadataAdvancedFetchOptionsComponent implements OnChanges, OnInit {

  @Output() metadataOptionsSubmitted = new EventEmitter<MetadataRefreshOptions>();
  @Input() currentMetadataOptions!: MetadataRefreshOptions;
  @Input() submitButtonLabel!: string;

  fields: (keyof FieldOptions)[] = [
    'title', 'subtitle', 'description', 'authors', 'publisher', 'publishedDate',
    'seriesName', 'seriesNumber', 'seriesTotal', 'isbn13', 'isbn10',
    'language', 'categories', 'cover'
  ];
  providers: string[] = [];

  refreshCovers: boolean = false;
  mergeCategories: boolean = false;
  reviewBeforeApply: boolean = false;

  allP1 = {placeholder: 'Set All', value: null as string | null};
  allP2 = {placeholder: 'Set All', value: null as string | null};
  allP3 = {placeholder: 'Set All', value: null as string | null};
  allP4 = {placeholder: 'Set All', value: null as string | null};

  fieldOptions: FieldOptions = this.initializeFieldOptions();

  private messageService = inject(MessageService);
  private appSettingsService = inject(AppSettingsService);

  ngOnInit(): void {
    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        const providerSettings = settings?.metadataProviderSettings ?? {};
        this.providers = Object.entries(providerSettings)
          .filter(([_, value]) => !!value && typeof value === 'object' && 'enabled' in value && (value as any).enabled)
          .map(([key]) => {
            // Handle special cases for provider names that don't follow simple capitalization
            const providerNameMap: {[key: string]: string} = {
              'iTunes': 'iTunes',
              'goodReads': 'GoodReads'
            };
            return providerNameMap[key] || key.charAt(0).toUpperCase() + key.slice(1);
          });
      });
  }

  private initializeFieldOptions(): FieldOptions {
    return this.fields.reduce((acc, field) => {
      acc[field] = {p1: null, p2: null, p3: null, p4: null};
      return acc;
    }, {} as FieldOptions);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currentMetadataOptions'] && this.currentMetadataOptions) {
      this.refreshCovers = this.currentMetadataOptions.refreshCovers || false;
      this.mergeCategories = this.currentMetadataOptions.mergeCategories || false;
      this.reviewBeforeApply = this.currentMetadataOptions.reviewBeforeApply || false;

      const backendFieldOptions = this.currentMetadataOptions.fieldOptions as FieldOptions || {};
      for (const field of this.fields) {
        if (!backendFieldOptions[field]) {
          backendFieldOptions[field] = {p1: null, p2: null, p3: null, p4: null};
        } else {
          backendFieldOptions[field].p4 ??= null;
        }
      }
      this.fieldOptions = backendFieldOptions;

      this.allP1.value = this.currentMetadataOptions.allP1 || null;
      this.allP2.value = this.currentMetadataOptions.allP2 || null;
      this.allP3.value = this.currentMetadataOptions.allP3 || null;
      this.allP4.value = this.currentMetadataOptions.allP4 || null;
    }
  }

  syncProvider(event: SelectChangeEvent, providerType: keyof FieldProvider) {
    for (const field of Object.keys(this.fieldOptions)) {
      this.fieldOptions[field as keyof FieldOptions][providerType] = event.value;
    }
  }

  submit() {
    const allFieldsHaveProvider = Object.values(this.fieldOptions).every(opt =>
      opt.p1 !== null || opt.p2 !== null || opt.p3 !== null || opt.p4 !== null
    );

    if (allFieldsHaveProvider) {
      const metadataRefreshOptions: MetadataRefreshOptions = {
        allP1: this.allP1.value,
        allP2: this.allP2.value,
        allP3: this.allP3.value,
        allP4: this.allP4.value,
        refreshCovers: this.refreshCovers,
        mergeCategories: this.mergeCategories,
        reviewBeforeApply: this.reviewBeforeApply,
        fieldOptions: this.fieldOptions
      };
      this.metadataOptionsSubmitted.emit(metadataRefreshOptions);
    } else {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'At least one provider (P1–P4) must be selected for each book field.',
        life: 5000
      });
    }
  }

  reset() {
    this.allP1.value = null;
    this.allP2.value = null;
    this.allP3.value = null;
    this.allP4.value = null;
    for (const field of Object.keys(this.fieldOptions)) {
      this.fieldOptions[field as keyof FieldOptions] = {
        p1: null,
        p2: null,
        p3: null,
        p4: null
      };
    }
  }

  formatLabel(field: string): string {
    return field.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase()).trim();
  }
}
