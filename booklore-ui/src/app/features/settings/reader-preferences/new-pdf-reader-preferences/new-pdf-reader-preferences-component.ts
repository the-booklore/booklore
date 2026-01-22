import {Component, inject, Input} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {PdfBackgroundColor, PdfFitMode, PdfPageSpread, PdfPageViewMode, PdfScrollMode, UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {TooltipModule} from 'primeng/tooltip';

@Component({
  selector: 'app-new-pdf-reader-preferences-component',
  imports: [
    FormsModule,
    TooltipModule
  ],
  templateUrl: './new-pdf-reader-preferences-component.html',
  styleUrl: './new-pdf-reader-preferences-component.scss'
})
export class NewPdfReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  private static readonly SETTING_ROOT = 'newPdfReaderSetting';
  private static readonly PROP_PAGE_SPREAD = 'pageSpread';
  private static readonly PROP_PAGE_VIEW_MODE = 'pageViewMode';
  private static readonly PROP_FIT_MODE = 'fitMode';
  private static readonly PROP_SCROLL_MODE = 'scrollMode';
  private static readonly PROP_BACKGROUND_COLOR = 'backgroundColor';

  readonly pdfSpreads = [
    {name: 'Even', key: PdfPageSpread.EVEN, icon: 'pi pi-align-left'},
    {name: 'Odd', key: PdfPageSpread.ODD, icon: 'pi pi-align-right'}
  ];

  readonly pdfViewModes = [
    {name: 'Single Page', key: PdfPageViewMode.SINGLE_PAGE, icon: 'pi pi-book'},
    {name: 'Two Page', key: PdfPageViewMode.TWO_PAGE, icon: 'pi pi-copy'},
  ];

  readonly pdfFitModes = [
    {name: 'Fit Page', key: PdfFitMode.FIT_PAGE, icon: 'pi pi-window-maximize'},
    {name: 'Fit Width', key: PdfFitMode.FIT_WIDTH, icon: 'pi pi-arrows-h'},
    {name: 'Fit Height', key: PdfFitMode.FIT_HEIGHT, icon: 'pi pi-arrows-v'},
    {name: 'Actual Size', key: PdfFitMode.ACTUAL_SIZE, icon: 'pi pi-expand'},
    {name: 'Automatic', key: PdfFitMode.AUTO, icon: 'pi pi-sparkles'}
  ];

  readonly pdfScrollModes = [
    {name: 'Paginated', key: PdfScrollMode.PAGINATED, icon: 'pi pi-book'},
    {name: 'Infinite', key: PdfScrollMode.INFINITE, icon: 'pi pi-sort-alt'}
  ];

  readonly pdfBackgroundColors = [
    {name: 'Gray', key: PdfBackgroundColor.GRAY, color: '#808080'},
    {name: 'Black', key: PdfBackgroundColor.BLACK, color: '#000000'},
    {name: 'White', key: PdfBackgroundColor.WHITE, color: '#FFFFFF'}
  ];

  get selectedPdfSpread(): PdfPageSpread {
    return this.userSettings.newPdfReaderSetting.pageSpread ?? PdfPageSpread.EVEN;
  }

  set selectedPdfSpread(value: PdfPageSpread) {
    this.userSettings.newPdfReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference([NewPdfReaderPreferencesComponent.SETTING_ROOT, NewPdfReaderPreferencesComponent.PROP_PAGE_SPREAD], value);
  }

  get selectedPdfViewMode(): PdfPageViewMode {
    return this.userSettings.newPdfReaderSetting.pageViewMode ?? PdfPageViewMode.SINGLE_PAGE;
  }

  set selectedPdfViewMode(value: PdfPageViewMode) {
    this.userSettings.newPdfReaderSetting.pageViewMode = value;
    this.readerPreferencesService.updatePreference([NewPdfReaderPreferencesComponent.SETTING_ROOT, NewPdfReaderPreferencesComponent.PROP_PAGE_VIEW_MODE], value);
  }

  get selectedPdfFitMode(): PdfFitMode {
    return this.userSettings.newPdfReaderSetting.fitMode ?? PdfFitMode.FIT_PAGE;
  }

  set selectedPdfFitMode(value: PdfFitMode) {
    this.userSettings.newPdfReaderSetting.fitMode = value;
    this.readerPreferencesService.updatePreference([NewPdfReaderPreferencesComponent.SETTING_ROOT, NewPdfReaderPreferencesComponent.PROP_FIT_MODE], value);
  }

  get selectedPdfScrollMode(): PdfScrollMode {
    return this.userSettings.newPdfReaderSetting.scrollMode ?? PdfScrollMode.PAGINATED;
  }

  set selectedPdfScrollMode(value: PdfScrollMode) {
    this.userSettings.newPdfReaderSetting.scrollMode = value;
    this.readerPreferencesService.updatePreference([NewPdfReaderPreferencesComponent.SETTING_ROOT, NewPdfReaderPreferencesComponent.PROP_SCROLL_MODE], value);
  }

  get selectedPdfBackgroundColor(): PdfBackgroundColor {
    return this.userSettings.newPdfReaderSetting.backgroundColor ?? PdfBackgroundColor.GRAY;
  }

  set selectedPdfBackgroundColor(value: PdfBackgroundColor) {
    this.userSettings.newPdfReaderSetting.backgroundColor = value;
    this.readerPreferencesService.updatePreference([NewPdfReaderPreferencesComponent.SETTING_ROOT, NewPdfReaderPreferencesComponent.PROP_BACKGROUND_COLOR], value);
  }
}
