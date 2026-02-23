import {Component, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';

import {Checkbox} from 'primeng/checkbox';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {Observable} from 'rxjs';
import {AppSettingKey, AppSettings, OidcProviderDetails} from '../../../shared/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../../../features/book/model/library.model';
import {LibraryService} from '../../../features/book/service/library.service';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-authentication-settings',
  templateUrl: './authentication-settings.component.html',
  standalone: true,
  imports: [
    FormsModule,
    InputText,
    Checkbox,
    ToggleSwitch,
    Button,
    MultiSelect,
    ReactiveFormsModule,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  styleUrls: ['./authentication-settings.component.scss']
})
export class AuthenticationSettingsComponent implements OnInit {
  availablePermissions = [
    {label: 'Upload Books', value: 'permissionUpload', selected: false, translationKey: 'perms.uploadBooks'},
    {label: 'Download Books', value: 'permissionDownload', selected: false, translationKey: 'perms.downloadBooks'},
    {label: 'Edit Book Metadata', value: 'permissionEditMetadata', selected: false, translationKey: 'perms.editMetadata'},
    {label: 'Manage Library', value: 'permissionManipulateLibrary', selected: false, translationKey: 'perms.manageLibrary'},
    {label: 'Email Book', value: 'permissionEmailBook', selected: false, translationKey: 'perms.emailBook'},
    {label: 'Delete Book', value: 'permissionDeleteBook', selected: false, translationKey: 'perms.deleteBook'},
    {label: 'KOReader Sync', value: 'permissionSyncKoreader', selected: false, translationKey: 'perms.koreaderSync'},
    {label: 'Kobo Sync', value: 'permissionSyncKobo', selected: false, translationKey: 'perms.koboSync'},
    {label: 'Access OPDS', value: 'permissionAccessOpds', selected: false, translationKey: 'perms.accessOpds'}
  ];

  internalAuthEnabled = true;
  autoUserProvisioningEnabled = false;
  selectedPermissions: string[] = [];
  oidcEnabled = false;
  allLibraries: Library[] = [];
  editingLibraryIds: number[] = [];

  oidcProvider: OidcProviderDetails = {
    providerName: '',
    clientId: '',
    issuerUri: '',
    claimMapping: {
      username: '',
      email: '',
      name: ''
    }
  };

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private t = inject(TranslocoService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$.pipe(
      filter((settings): settings is AppSettings => settings != null),
      take(1)
    ).subscribe(settings => this.loadSettings(settings));

    this.libraryService.libraryState$
      .pipe(
        filter(state => !!state?.loaded),
        take(1)
      ).subscribe(state => this.allLibraries = state.libraries ?? []);
  }

  loadSettings(settings: AppSettings): void {
    this.oidcEnabled = settings.oidcEnabled;

    const details = settings.oidcAutoProvisionDetails;

    this.autoUserProvisioningEnabled = details?.enableAutoProvisioning ?? false;
    this.selectedPermissions = details?.defaultPermissions ?? [];
    this.editingLibraryIds = details?.defaultLibraryIds ?? [];

    const defaultClaimMapping = {
      username: 'preferred_username',
      email: 'email',
      name: 'given_name'
    };

    this.oidcProvider = {
      providerName: settings.oidcProviderDetails?.providerName || '',
      clientId: settings.oidcProviderDetails?.clientId || '',
      issuerUri: settings.oidcProviderDetails?.issuerUri || '',
      claimMapping: settings.oidcProviderDetails?.claimMapping || defaultClaimMapping
    };

    this.availablePermissions.forEach(perm => {
      perm.selected = this.selectedPermissions.includes(perm.value);
    });
  }

  isOidcFormComplete(): boolean {
    const p = this.oidcProvider;
    return !!(p.providerName && p.clientId && p.issuerUri && p.claimMapping.name && p.claimMapping.email && p.claimMapping.username);
  }

  toggleOidcEnabled(): void {
    if (!this.isOidcFormComplete()) return;
    this.appSettingsService.toggleOidcEnabled(this.oidcEnabled).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.oidcUpdated')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.oidcError')
      })
    });
  }

  saveOidcProvider(): void {
    const payload = [
      {
        key: AppSettingKey.OIDC_PROVIDER_DETAILS,
        newValue: this.oidcProvider
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.providerSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.providerError')
      })
    });
  }

  saveOidcAutoProvisionSettings(): void {
    const provisionDetails = {
      enableAutoProvisioning: this.autoUserProvisioningEnabled,
      defaultPermissions: [
        'permissionRead',
        ...this.availablePermissions.filter(p => p.selected).map(p => p.value)
      ],
      defaultLibraryIds: this.editingLibraryIds
    };

    const payload = [
      {
        key: AppSettingKey.OIDC_AUTO_PROVISION_DETAILS,
        newValue: provisionDetails
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.provisionSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.provisionError')
      })
    });
  }
}
