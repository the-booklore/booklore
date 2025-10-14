import {Component, inject, Input} from '@angular/core';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule} from '@angular/forms';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences-service';

@Component({
  selector: 'app-cbx-reader-preferences-component',
  imports: [
    RadioButton,
    FormsModule
  ],
  templateUrl: './cbx-reader-preferences-component.html',
  styleUrl: './cbx-reader-preferences-component.scss'
})
export class CbxReaderPreferencesComponent {

  @Input() userSettings!: UserSettings;

  private readonly readerPreferencesService = inject(ReaderPreferencesService);

  private static readonly SETTING_ROOT = 'cbxReaderSetting';
  private static readonly PROP_PAGE_SPREAD = 'pageSpread';
  private static readonly PROP_PAGE_VIEW_MODE = 'pageViewMode';
  private static readonly PROP_FIT_MODE = 'fitMode';
  private static readonly PROP_SCROLL_MODE = 'scrollMode';
  private static readonly PROP_BACKGROUND_COLOR = 'backgroundColor';

  readonly cbxSpreads = [
    {name: 'Even', key: CbxPageSpread.EVEN},
    {name: 'Odd', key: CbxPageSpread.ODD}
  ];

  readonly cbxViewModes = [
    {name: 'Single Page', key: CbxPageViewMode.SINGLE_PAGE},
    {name: 'Two Page', key: CbxPageViewMode.TWO_PAGE},
  ];

  readonly cbxFitModes = [
    {name: 'Fit Page', key: CbxFitMode.FIT_PAGE},
    {name: 'Fit Width', key: CbxFitMode.FIT_WIDTH},
    {name: 'Fit Height', key: CbxFitMode.FIT_HEIGHT},
    {name: 'Actual Size', key: CbxFitMode.ACTUAL_SIZE},
    {name: 'Automatic', key: CbxFitMode.AUTO}
  ];

  readonly cbxScrollModes = [
    {name: 'Paginated', key: CbxScrollMode.PAGINATED},
    {name: 'Infinite', key: CbxScrollMode.INFINITE}
  ];

  readonly cbxBackgroundColors = [
    {name: 'Gray', key: CbxBackgroundColor.GRAY},
    {name: 'Black', key: CbxBackgroundColor.BLACK},
    {name: 'White', key: CbxBackgroundColor.WHITE}
  ];

  get selectedCbxSpread(): CbxPageSpread {
    return this.userSettings.cbxReaderSetting.pageSpread ?? CbxPageSpread.EVEN;
  }

  set selectedCbxSpread(value: CbxPageSpread) {
    this.userSettings.cbxReaderSetting.pageSpread = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_PAGE_SPREAD], value);
  }

  get selectedCbxViewMode(): CbxPageViewMode {
    return this.userSettings.cbxReaderSetting.pageViewMode ?? CbxPageViewMode.SINGLE_PAGE;
  }

  set selectedCbxViewMode(value: CbxPageViewMode) {
    this.userSettings.cbxReaderSetting.pageViewMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_PAGE_VIEW_MODE], value);
  }

  get selectedCbxFitMode(): CbxFitMode {
    return this.userSettings.cbxReaderSetting.fitMode ?? CbxFitMode.FIT_PAGE;
  }

  set selectedCbxFitMode(value: CbxFitMode) {
    this.userSettings.cbxReaderSetting.fitMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_FIT_MODE], value);
  }

  get selectedCbxScrollMode(): CbxScrollMode {
    return this.userSettings.cbxReaderSetting.scrollMode ?? CbxScrollMode.PAGINATED;
  }

  set selectedCbxScrollMode(value: CbxScrollMode) {
    this.userSettings.cbxReaderSetting.scrollMode = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_SCROLL_MODE], value);
  }

  get selectedCbxBackgroundColor(): CbxBackgroundColor {
    return this.userSettings.cbxReaderSetting.backgroundColor ?? CbxBackgroundColor.GRAY;
  }

  set selectedCbxBackgroundColor(value: CbxBackgroundColor) {
    this.userSettings.cbxReaderSetting.backgroundColor = value;
    this.readerPreferencesService.updatePreference([CbxReaderPreferencesComponent.SETTING_ROOT, CbxReaderPreferencesComponent.PROP_BACKGROUND_COLOR], value);
  }
}
