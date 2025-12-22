import {Component, inject, Input} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ReaderPreferencesService} from '../reader-preferences-service';
import {PageSpread, UserSettings} from '../../user-management/user.service';
import {TooltipModule} from 'primeng/tooltip';

@Component({
  selector: 'app-pdf-reader-preferences-component',
  imports: [
    FormsModule,
    TooltipModule
  ],
  templateUrl: './pdf-reader-preferences-component.html',
  styleUrl: './pdf-reader-preferences-component.scss'
})
export class PdfReaderPreferencesComponent {
  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  @Input() userSettings!: UserSettings;

  readonly spreads: Array<{name: string; key: PageSpread; icon: string}> = [
    {name: 'Even', key: 'even', icon: 'pi pi-align-left'},
    {name: 'Odd', key: 'odd', icon: 'pi pi-align-right'},
    {name: 'None', key: 'off', icon: 'pi pi-minus'}
  ];

  readonly zooms: Array<{name: string; key: string; icon: string}> = [
    {name: 'Auto Zoom', key: 'auto', icon: 'pi pi-sparkles'},
    {name: 'Page Fit', key: 'page-fit', icon: 'pi pi-window-maximize'},
    {name: 'Page Width', key: 'page-width', icon: 'pi pi-arrows-h'},
    {name: 'Actual Size', key: 'page-actual', icon: 'pi pi-expand'}
  ];

  get selectedSpread(): 'even' | 'odd' | 'off' {
    return this.userSettings.pdfReaderSetting.pageSpread;
  }

  set selectedSpread(value: 'even' | 'odd' | 'off') {
    this.userSettings.pdfReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference(['pdfReaderSetting', 'pageSpread'], value);
  }

  get selectedZoom(): string {
    return this.userSettings.pdfReaderSetting.pageZoom;
  }

  set selectedZoom(value: string) {
    this.userSettings.pdfReaderSetting.pageZoom = value;
    this.readerPreferencesService.updatePreference(['pdfReaderSetting', 'pageZoom'], value);
  }
}
