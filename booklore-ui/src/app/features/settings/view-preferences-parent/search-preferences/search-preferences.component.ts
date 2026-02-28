import {Component, inject, OnInit} from '@angular/core';
import {MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {SearchPreferenceService, SearchTriggerMode} from '../../../../shared/service/search-preference.service';

@Component({
  selector: 'app-search-preferences',
  standalone: true,
  imports: [
    TranslocoDirective,
  ],
  templateUrl: './search-preferences.component.html',
  styleUrl: './search-preferences.component.scss',
})
export class SearchPreferencesComponent implements OnInit {

  selectedMode: SearchTriggerMode = 'instant';

  private readonly searchPrefService = inject(SearchPreferenceService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  ngOnInit(): void {
    this.selectedMode = this.searchPrefService.mode;
  }

  onModeChange(value: SearchTriggerMode): void {
    this.selectedMode = value;
    this.searchPrefService.setMode(value);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.search.prefsUpdated'),
      detail: this.t.translate('settingsView.search.prefsUpdatedDetail'),
      life: 1500,
    });
  }
}
