import {Component, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Checkbox} from 'primeng/checkbox';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {filter, take} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {AppSettingKey} from '../../../../shared/model/app-settings.model';
import {Select} from 'primeng/select';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';

@Component({
  selector: 'app-metadata-provider-settings',
  imports: [
    ReactiveFormsModule,
    TableModule,
    Checkbox,
    InputText,
    Button,
    FormsModule,
    Select,
    ExternalDocLinkComponent
  ],
  templateUrl: './metadata-provider-settings.component.html',
  styleUrl: './metadata-provider-settings.component.scss'
})
export class MetadataProviderSettingsComponent implements OnInit {

  amazonDomains = [
    {label: 'amazon.com', value: 'com'},
    {label: 'amazon.de', value: 'de'},
    {label: 'amazon.co.uk', value: 'co.uk'},
    {label: 'amazon.co.jp', value: 'co.jp'},
    {label: 'amazon.ca', value: 'ca'},
    {label: 'amazon.in', value: 'in'},
    {label: 'amazon.com.au', value: 'com.au'},
    {label: 'amazon.fr', value: 'fr'},
    {label: 'amazon.it', value: 'it'},
    {label: 'amazon.es', value: 'es'},
    {label: 'amazon.nl', value: 'nl'},
    {label: 'amazon.se', value: 'se'},
    {label: 'amazon.com.br', value: 'com.br'},
    {label: 'amazon.sg', value: 'sg'},
    {label: 'amazon.com.mx', value: 'com.mx'},
    {label: 'amazon.pl', value: 'pl'},
    {label: 'amazon.ae', value: 'ae'},
    {label: 'amazon.sa', value: 'sa'},
    {label: 'amazon.tr', value: 'tr'}
  ];

  selectedAmazonDomain = 'com';

  googleLanguages = [
    {label: 'Dutch', value: 'nl'},
    {label: 'English', value: 'en'},
    {label: 'French', value: 'fr'},
    {label: 'German', value: 'de'},
    {label: 'Italian', value: 'it'},
    {label: 'Japanese', value: 'ja'},
    {label: 'Polish', value: 'pl'},
    {label: 'Portuguese', value: 'pt'},
    {label: 'Spanish', value: 'es'},
    {label: 'Swedish', value: 'sv'}
  ];

  selectedGoogleLanguage = '';

  koboCountries = [
    {label: 'Argentina', value: 'ar', language: 'es'},
    {label: 'Australia', value: 'au', language: 'en'},
    {label: 'Austria', value: 'at', language: 'de'},
    {label: 'Belgium', value: 'be', language: 'fr'},
    {label: 'Brasil', value: 'br', language: 'pt'},
    {label: 'Canada', value: 'ca', language: 'en'},
    {label: 'Chile', value: 'cl', language: 'es'},
    {label: 'Colômbia', value: 'co', language: 'es'},
    {label: 'Cyprus', value: 'cy', language: 'en'},
    {label: 'Czech Republic', value: 'cz', language: 'cs'},
    {label: 'Denmark', value: 'dk', language: 'da'},
    {label: 'Estonia', value: 'ee', language: 'en'},
    {label: 'Finland', value: 'fi', language: 'fi'},
    {label: 'France', value: 'fr', language: 'fr'},
    {label: 'Germany', value: 'de', language: 'de'},
    {label: 'Greece', value: 'gr', language: 'en'},
    {label: 'Hong Kong', value: 'hk', language: 'zh'},
    {label: 'India', value: 'in', language: 'en'},
    {label: 'Ireland', value: 'ie', language: 'en'},
    {label: 'Italy', value: 'it', language: 'it'},
    {label: 'Japan', value: 'jp', language: 'ja'},
    {label: 'Lithuania', value: 'lt', language: 'en'},
    {label: 'Luxembourg', value: 'lu', language: 'fr'},
    {label: 'Malaysia', value: 'my', language: 'en'},
    {label: 'Malta', value: 'mt', language: 'en'},
    {label: 'México', value: 'mx', language: 'es'},
    {label: 'Netherlands', value: 'nl', language: 'nl'},
    {label: 'New Zealand', value: 'nz', language: 'en'},
    {label: 'Norway', value: 'no', language: 'nb'},
    {label: 'Peru', value: 'pe', language: 'es'},
    {label: 'Philippines', value: 'ph', language: 'en'},
    {label: 'Poland', value: 'pl', language: 'pl'},
    {label: 'Portugal', value: 'pt', language: 'pt'},
    {label: 'Romania', value: 'ro', language: 'ro'},
    {label: 'Singapore', value: 'sg', language: 'en'},
    {label: 'Slovak Republic', value: 'sk', language: 'en'},
    {label: 'Slovenia', value: 'si', language: 'en'},
    {label: 'South Africa', value: 'za', language: 'en'},
    {label: 'Spain', value: 'es', language: 'es'},
    {label: 'Sweden', value: 'se', language: 'sv'},
    {label: 'Switzerland', value: 'ch', language: 'fr'},
    {label: 'Taiwan', value: 'tw', language: 'zh'},
    {label: 'Thailand', value: 'th', language: 'en'},
    {label: 'Turkey', value: 'tr', language: 'tr'},
    {label: 'United Kingdom', value: 'gb', language: 'en'},
    {label: 'United States', value: 'us', language: 'en'},
    {label: 'Other', value: 'ww', language: 'en'}
  ];

  koboLanguages = [
    {label: 'Chinese', value: 'zh'},
    {label: 'English', value: 'en'},
    {label: 'Czech', value: 'cs'},
    {label: 'Danish', value: 'da'},
    {label: 'Deutsch', value: 'de'},
    {label: 'Español', value: 'es'},
    {label: 'Finnish', value: 'fi'},
    {label: 'Français', value: 'fr'},
    {label: 'Italiano', value: 'it'},
    {label: 'Japanese', value: 'ja'},
    {label: 'Nederlands', value: 'nl'},
    {label: 'Norwegian', value: 'nb'},
    {label: 'Polish', value: 'pl'},
    {label: 'Português', value: 'pt'},
    {label: 'Romanian', value: 'ro'},
    {label: 'Swedish', value: 'sv'},
    {label: 'Turkish', value: 'tr'}
  ];

  hardcoverToken: string = '';
  amazonCookie: string = '';
  hardcoverEnabled: boolean = false;
  amazonEnabled: boolean = false;
  goodreadsEnabled: boolean = false;
  googleEnabled: boolean = false;
  comicvineEnabled: boolean = false;
  comicvineToken: string = '';
  doubanEnabled: boolean = false;
  lubimyCzytacEnabled: boolean = false;
  koboEnabled: boolean = false;
  koboCountry: string = 'us';
  koboLanguage: string = 'en';
  koboMaxResults: number = 5;
  koboResizeCover: boolean = true;

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);

  private appSettings$ = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        const metadataProviderSettings = settings!.metadataProviderSettings;
        this.amazonEnabled = metadataProviderSettings?.amazon?.enabled ?? false;
        this.amazonCookie = metadataProviderSettings?.amazon?.cookie ?? "";
        this.selectedAmazonDomain = metadataProviderSettings?.amazon?.domain ?? 'com';
        this.goodreadsEnabled = metadataProviderSettings?.goodReads?.enabled ?? false;
        this.googleEnabled = metadataProviderSettings?.google?.enabled ?? false;
        this.selectedGoogleLanguage = metadataProviderSettings?.google?.language ?? '';
        this.hardcoverToken = metadataProviderSettings?.hardcover?.apiKey ?? '';
        this.hardcoverEnabled = metadataProviderSettings?.hardcover?.enabled ?? false;
        this.comicvineEnabled = metadataProviderSettings?.comicvine?.enabled ?? false;
        this.comicvineToken = metadataProviderSettings?.comicvine?.apiKey ?? '';
        this.doubanEnabled = metadataProviderSettings?.douban?.enabled ?? false;
        this.lubimyCzytacEnabled = metadataProviderSettings?.lubimyczytac?.enabled ?? false;
        this.koboEnabled = metadataProviderSettings?.kobo?.enabled ?? false;
        this.koboCountry = metadataProviderSettings?.kobo?.country ?? 'us';
        this.koboLanguage = metadataProviderSettings?.kobo?.language ?? 'en';
        this.koboMaxResults = metadataProviderSettings?.kobo?.maxResults ?? 5;
        this.koboResizeCover = metadataProviderSettings?.kobo?.resizeCover ?? true;
      });
  }

  onTokenChange(newToken: string): void {
    this.hardcoverToken = newToken;
    if (!newToken.trim()) {
      this.hardcoverEnabled = false;
    }
  }

  onComicTokenChange(newToken: string): void {
    this.comicvineToken = newToken;
    if (!newToken.trim()) {
      this.comicvineEnabled = false;
    }
  }

  onKoboCountryChange(newCountry: string): void {
    this.koboCountry = newCountry;
    const country = this.koboCountries.find(c => c.value === newCountry);
    if (country?.language) {
      this.koboLanguage = country.language;
    }
  }

  saveSettings(): void {
    const payload = [
      {
        key: AppSettingKey.METADATA_PROVIDER_SETTINGS,
        newValue: {
          amazon: {
            enabled: this.amazonEnabled,
            cookie: this.amazonCookie,
            domain: this.selectedAmazonDomain
          },

          comicvine: {
            enabled: this.comicvineEnabled,
            apiKey: this.comicvineToken.trim()
          },

          goodReads: {enabled: this.goodreadsEnabled},
          google: {
            enabled: this.googleEnabled,
            language: this.selectedGoogleLanguage,
          },
          hardcover: {
            enabled: this.hardcoverEnabled,
            apiKey: this.hardcoverToken.trim()
          },
          kobo: {
            enabled: this.koboEnabled,
            country: this.koboCountry?.trim() || 'us',
            language: this.koboLanguage?.trim() || 'en',
            maxResults: Number(this.koboMaxResults) || 5,
            resizeCover: this.koboResizeCover
          },
          douban: {enabled: this.doubanEnabled},
          lubimyczytac: {enabled: this.lubimyCzytacEnabled}
        }
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () =>
        this.messageService.add({
          severity: 'success',
          summary: 'Saved',
          detail: 'Metadata provider settings saved.'
        }),
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to save metadata provider settings.'
        })
    });
  }
}
