import {Component, DestroyRef, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {filter} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ConfirmationService, MessageService} from 'primeng/api';
import {AppSettingKey, CustomMetadataProviderConfig} from '../../../../shared/model/app-settings.model';
import {Select} from 'primeng/select';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {CustomProviderService} from '../../../../shared/service/custom-provider.service';
import {ConfirmDialog} from 'primeng/confirmdialog';

@Component({
  selector: 'app-metadata-provider-settings',
  imports: [
    ReactiveFormsModule,
    TableModule,
    InputText,
    Button,
    FormsModule,
    Select,
    ExternalDocLinkComponent,
    ToggleSwitchModule,
    TranslocoDirective,
    ConfirmDialog
  ],
  providers: [ConfirmationService],
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

  audibleDomains = [
    {label: 'audible.com', value: 'com'},
    {label: 'audible.co.uk', value: 'co.uk'},
    {label: 'audible.de', value: 'de'},
    {label: 'audible.fr', value: 'fr'},
    {label: 'audible.it', value: 'it'},
    {label: 'audible.es', value: 'es'},
    {label: 'audible.ca', value: 'ca'},
    {label: 'audible.com.au', value: 'com.au'},
    {label: 'audible.co.jp', value: 'co.jp'},
    {label: 'audible.in', value: 'in'}
  ];

  selectedAudibleDomain = 'com';
  audibleEnabled: boolean = false;

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
  ranobedbEnabled: boolean = false;
  googleApiKey: string = '';

  // Custom providers
  customProviders: CustomMetadataProviderConfig[] = [];
  editingCustomProvider: CustomMetadataProviderConfig | null = null;
  customProviderForm = {name: '', baseUrl: '', bearerToken: '', enabled: true};
  showCustomProviderForm: boolean = false;
  validatingProvider: boolean = false;

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private destroyRef = inject(DestroyRef);
  private t = inject(TranslocoService);
  private customProviderService = inject(CustomProviderService);

  private appSettings$ = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(settings => {
        const metadataProviderSettings = settings!.metadataProviderSettings;
        this.amazonEnabled = metadataProviderSettings?.amazon?.enabled ?? false;
        this.amazonCookie = metadataProviderSettings?.amazon?.cookie ?? "";
        this.selectedAmazonDomain = metadataProviderSettings?.amazon?.domain ?? 'com';
        this.goodreadsEnabled = metadataProviderSettings?.goodReads?.enabled ?? false;
        this.googleEnabled = metadataProviderSettings?.google?.enabled ?? false;
        this.selectedGoogleLanguage = metadataProviderSettings?.google?.language ?? '';
        this.googleApiKey = metadataProviderSettings?.google?.apiKey ?? '';
        this.hardcoverToken = metadataProviderSettings?.hardcover?.apiKey ?? '';
        this.hardcoverEnabled = metadataProviderSettings?.hardcover?.enabled ?? false;
        this.comicvineEnabled = metadataProviderSettings?.comicvine?.enabled ?? false;
        this.comicvineToken = metadataProviderSettings?.comicvine?.apiKey ?? '';
        this.doubanEnabled = metadataProviderSettings?.douban?.enabled ?? false;
        this.lubimyCzytacEnabled = metadataProviderSettings?.lubimyczytac?.enabled ?? false;
        this.ranobedbEnabled = metadataProviderSettings?.ranobedb?.enabled ?? false;
        this.audibleEnabled = metadataProviderSettings?.audible?.enabled ?? false;
        this.selectedAudibleDomain = metadataProviderSettings?.audible?.domain ?? 'com';
        this.customProviders = metadataProviderSettings?.customProviders
          ? metadataProviderSettings.customProviders.map(p => ({...p}))
          : [];
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
  }

  // --- Custom Provider Methods ---

  startAddCustomProvider(): void {
    this.editingCustomProvider = null;
    this.customProviderForm = {name: '', baseUrl: '', bearerToken: '', enabled: true};
    this.showCustomProviderForm = true;
  }

  startEditCustomProvider(provider: CustomMetadataProviderConfig): void {
    this.editingCustomProvider = provider;
    this.customProviderForm = {
      name: provider.name,
      baseUrl: provider.baseUrl,
      bearerToken: provider.bearerToken ?? '',
      enabled: provider.enabled
    };
    this.showCustomProviderForm = true;
  }

  cancelCustomProviderForm(): void {
    this.editingCustomProvider = null;
    this.customProviderForm = {name: '', baseUrl: '', bearerToken: '', enabled: true};
    this.showCustomProviderForm = false;
  }

  testCustomProvider(): void {
    const testConfig: CustomMetadataProviderConfig = {
      id: this.editingCustomProvider?.id ?? 'test',
      name: this.customProviderForm.name || 'Test',
      baseUrl: this.customProviderForm.baseUrl,
      bearerToken: this.customProviderForm.bearerToken || undefined,
      enabled: true
    };

    this.validatingProvider = true;
    this.customProviderService.validateProvider(testConfig).subscribe({
      next: (capabilities) => {
        this.validatingProvider = false;
        if (!this.customProviderForm.name && capabilities.providerName) {
          this.customProviderForm.name = capabilities.providerName;
        }
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.providers.custom.testSuccess', {name: capabilities.providerName ?? 'Provider'})
        });
      },
      error: () => {
        this.validatingProvider = false;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.providers.custom.testError')
        });
      }
    });
  }

  saveCustomProvider(): void {
    const form = this.customProviderForm;
    if (!form.baseUrl.trim()) return;

    if (this.editingCustomProvider) {
      // Update existing
      const existing = this.customProviders.find(p => p.id === this.editingCustomProvider!.id);
      if (existing) {
        existing.name = form.name.trim() || existing.name;
        existing.baseUrl = form.baseUrl.trim();
        existing.bearerToken = form.bearerToken.trim() || undefined;
        existing.enabled = form.enabled;
      }
    } else {
      // Add new
      const newProvider: CustomMetadataProviderConfig = {
        id: crypto.randomUUID(),
        name: form.name.trim() || 'Custom Provider',
        baseUrl: form.baseUrl.trim(),
        bearerToken: form.bearerToken.trim() || undefined,
        enabled: form.enabled
      };
      this.customProviders.push(newProvider);
    }

    this.editingCustomProvider = null;
    this.customProviderForm = {name: '', baseUrl: '', bearerToken: '', enabled: true};
    this.showCustomProviderForm = false;
  }

  removeCustomProvider(event: Event, provider: CustomMetadataProviderConfig): void {
    this.confirmationService.confirm({
      target: event.target as EventTarget,
      message: this.t.translate('settingsMeta.providers.custom.removeConfirm', {name: provider.name}),
      accept: () => {
        this.customProviders = this.customProviders.filter(p => p.id !== provider.id);
      }
    });
  }

  toggleCustomProvider(provider: CustomMetadataProviderConfig): void {
    provider.enabled = !provider.enabled;
  }

  // --- Save ---

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
            apiKey: this.googleApiKey.trim()
          },
          hardcover: {
            enabled: this.hardcoverEnabled,
            apiKey: this.hardcoverToken.trim()
          },
          douban: {enabled: this.doubanEnabled},
          lubimyczytac: {enabled: this.lubimyCzytacEnabled},
          ranobedb: {enabled: this.ranobedbEnabled},
          audible: {
            enabled: this.audibleEnabled,
            domain: this.selectedAudibleDomain
          },
          customProviders: this.customProviders
        }
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.providers.saveSuccess')
        });
        // Refresh the custom provider registry on the backend
        this.customProviderService.refreshRegistry().subscribe();
      },
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.providers.saveError')
        })
    });
  }
}
