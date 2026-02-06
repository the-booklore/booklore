import {Component, inject, Input, OnInit} from '@angular/core';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {AppSettingKey, MetadataProviderSpecificFields} from '../../../../shared/model/app-settings.model';
import {filter, take} from 'rxjs/operators';

@Component({
  selector: 'app-metadata-provider-field-selector',
  standalone: true,
  imports: [ToggleSwitchModule, FormsModule],
  templateUrl: './metadata-provider-field-selector.component.html',
  styleUrl: './metadata-provider-field-selector.component.scss'
})
export class MetadataProviderFieldSelectorComponent implements OnInit {
  @Input() selectedFields: string[] = [];

  private appSettingsService = inject(AppSettingsService);

  providerGroups: { label: string, fields: string[] }[] = [
    {
      label: 'Amazon',
      fields: ['asin', 'amazonRating', 'amazonReviewCount']
    },
    {
      label: 'Google Books',
      fields: ['googleId']
    },
    {
      label: 'Goodreads',
      fields: ['goodreadsId', 'goodreadsRating', 'goodreadsReviewCount']
    },
    {
      label: 'Hardcover',
      fields: ['hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount']
    },
    {
      label: 'Comicvine',
      fields: ['comicvineId']
    },
    {
      label: 'Lubimyczytac',
      fields: ['lubimyczytacId', 'lubimyczytacRating']
    },
    {
      label: 'Ranobedb',
      fields: ['ranobedbId', 'ranobedbRating']
    },
    {
      label: 'Audible',
      fields: ['audibleId', 'audibleRating', 'audibleReviewCount']
    }
  ];

  fieldLabels: Record<string, string> = {
    'asin': 'Amazon ASIN',
    'amazonRating': 'Amazon Rating',
    'amazonReviewCount': 'Amazon Review Count',
    'googleId': 'Google Books ID',
    'goodreadsId': 'Goodreads ID',
    'goodreadsRating': 'Goodreads Rating',
    'goodreadsReviewCount': 'Goodreads Review Count',
    'hardcoverId': 'Hardcover ID',
    'hardcoverBookId': 'Hardcover Book ID',
    'hardcoverRating': 'Hardcover Rating',
    'hardcoverReviewCount': 'Hardcover Review Count',
    'comicvineId': 'Comicvine ID',
    'lubimyczytacId': 'Lubimyczytac ID',
    'lubimyczytacRating': 'Lubimyczytac Rating',
    'ranobedbId': 'Ranobedb ID',
    'ranobedbRating': 'Ranobedb Rating',
    'audibleId': 'Audible ID',
    'audibleRating': 'Audible Rating',
    'audibleReviewCount': 'Audible Review Count',
  };

  private readonly allFieldNames: (keyof MetadataProviderSpecificFields)[] = [
    'asin', 'amazonRating', 'amazonReviewCount',
    'googleId',
    'goodreadsId', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount',
    'comicvineId',
    'lubimyczytacId', 'lubimyczytacRating',
    'ranobedbId', 'ranobedbRating',
    'audibleId', 'audibleRating', 'audibleReviewCount'
  ];

  ngOnInit(): void {
    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => !!settings?.metadataProviderSpecificFields),
        take(1)
      )
      .subscribe(settings => {
        if (settings?.metadataProviderSpecificFields) {
          this.selectedFields = this.toFieldArray(settings.metadataProviderSpecificFields);
        }
      });
  }

  toggleField(field: string, checked: boolean) {
    this.selectedFields = checked
      ? [...this.selectedFields, field]
      : this.selectedFields.filter(f => f !== field);

    this.appSettingsService.saveSettings([{
      key: AppSettingKey.METADATA_PROVIDER_SPECIFIC_FIELDS ?? 'metadataProviderSpecificFields',
      newValue: this.toFieldState(this.selectedFields)
    }]).subscribe();
  }

  private toFieldArray(fieldState: MetadataProviderSpecificFields): string[] {
    const selectedFields: string[] = [];
    for (const [field, enabled] of Object.entries(fieldState)) {
      if (enabled) {
        selectedFields.push(field);
      }
    }
    return selectedFields;
  }

  private toFieldState(selectedFields: string[]): MetadataProviderSpecificFields {
    const fieldState: any = {};
    for (const field of this.allFieldNames) {
      fieldState[field] = selectedFields.includes(field);
    }
    return fieldState;
  }
}
