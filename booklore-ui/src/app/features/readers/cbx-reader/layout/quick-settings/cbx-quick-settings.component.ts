import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {
  CbxFitMode,
  CbxScrollMode,
  CbxPageViewMode,
  CbxPageSpread,
  CbxBackgroundColor,
  PdfPageViewMode,
  PdfPageSpread
} from '../../../../settings/user-management/user.service';
import {ReaderIconComponent, ReaderIconName} from '../../../ebook-reader/shared/icon.component';
import {CbxQuickSettingsService, CbxQuickSettingsState} from './cbx-quick-settings.service';

@Component({
  selector: 'app-cbx-quick-settings',
  standalone: true,
  imports: [CommonModule, ReaderIconComponent],
  templateUrl: './cbx-quick-settings.component.html',
  styleUrls: ['./cbx-quick-settings.component.scss']
})
export class CbxQuickSettingsComponent implements OnInit, OnDestroy {
  private quickSettingsService = inject(CbxQuickSettingsService);
  private destroy$ = new Subject<void>();

  state: CbxQuickSettingsState = {
    fitMode: CbxFitMode.FIT_PAGE,
    scrollMode: CbxScrollMode.PAGINATED,
    pageViewMode: CbxPageViewMode.SINGLE_PAGE,
    pageSpread: CbxPageSpread.ODD,
    backgroundColor: CbxBackgroundColor.GRAY
  };

  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;

  fitModeOptions: {value: CbxFitMode, label: string, icon: ReaderIconName}[] = [
    {value: CbxFitMode.FIT_PAGE, label: 'Fit Page', icon: 'fit-page'},
    {value: CbxFitMode.FIT_WIDTH, label: 'Fit Width', icon: 'fit-width'},
    {value: CbxFitMode.FIT_HEIGHT, label: 'Fit Height', icon: 'fit-height'},
    {value: CbxFitMode.ACTUAL_SIZE, label: 'Actual Size', icon: 'actual-size'},
    {value: CbxFitMode.AUTO, label: 'Automatic', icon: 'auto-fit'}
  ];

  backgroundOptions = [
    {value: CbxBackgroundColor.BLACK, label: 'Black', color: '#000000'},
    {value: CbxBackgroundColor.GRAY, label: 'Gray', color: '#808080'},
    {value: CbxBackgroundColor.WHITE, label: 'White', color: '#ffffff'}
  ];

  ngOnInit(): void {
    this.quickSettingsService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => this.state = state);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isTwoPageView(): boolean {
    return this.state.pageViewMode === CbxPageViewMode.TWO_PAGE || this.state.pageViewMode === PdfPageViewMode.TWO_PAGE;
  }

  get isPaginated(): boolean {
    return this.state.scrollMode === CbxScrollMode.PAGINATED;
  }

  get isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  onFitModeSelect(mode: CbxFitMode): void {
    this.quickSettingsService.emitFitModeChange(mode);
  }

  onScrollModeToggle(): void {
    const newMode = this.state.scrollMode === CbxScrollMode.PAGINATED
      ? CbxScrollMode.INFINITE
      : CbxScrollMode.PAGINATED;
    this.quickSettingsService.emitScrollModeChange(newMode);
  }

  onPageViewToggle(): void {
    const newMode = this.state.pageViewMode === CbxPageViewMode.SINGLE_PAGE
      ? CbxPageViewMode.TWO_PAGE
      : CbxPageViewMode.SINGLE_PAGE;
    this.quickSettingsService.emitPageViewModeChange(newMode);
  }

  onPageSpreadToggle(): void {
    const newSpread = this.state.pageSpread === CbxPageSpread.ODD
      ? CbxPageSpread.EVEN
      : CbxPageSpread.ODD;
    this.quickSettingsService.emitPageSpreadChange(newSpread);
  }

  onBackgroundSelect(color: CbxBackgroundColor): void {
    this.quickSettingsService.emitBackgroundColorChange(color);
  }

  onOverlayClick(): void {
    this.quickSettingsService.close();
  }
}
