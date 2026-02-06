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
  CbxReadingDirection,
  CbxSlideshowInterval,
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
    backgroundColor: CbxBackgroundColor.GRAY,
    readingDirection: CbxReadingDirection.LTR,
    slideshowInterval: CbxSlideshowInterval.FIVE_SECONDS
  };

  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;
  protected readonly CbxReadingDirection = CbxReadingDirection;
  protected readonly CbxSlideshowInterval = CbxSlideshowInterval;

  fitModeOptions: {value: CbxFitMode, label: string, icon: ReaderIconName}[] = [
    {value: CbxFitMode.FIT_PAGE, label: 'Fit Page', icon: 'fit-page'},
    {value: CbxFitMode.FIT_WIDTH, label: 'Fit Width', icon: 'fit-width'},
    {value: CbxFitMode.FIT_HEIGHT, label: 'Fit Height', icon: 'fit-height'},
    {value: CbxFitMode.ACTUAL_SIZE, label: 'Actual Size', icon: 'actual-size'},
    {value: CbxFitMode.AUTO, label: 'Automatic', icon: 'auto-fit'}
  ];

  scrollModeOptions: {value: CbxScrollMode, label: string}[] = [
    {value: CbxScrollMode.PAGINATED, label: 'Paginated'},
    {value: CbxScrollMode.INFINITE, label: 'Infinite'},
    {value: CbxScrollMode.LONG_STRIP, label: 'Long Strip'}
  ];

  slideshowIntervalOptions: {value: CbxSlideshowInterval, label: string}[] = [
    {value: CbxSlideshowInterval.THREE_SECONDS, label: '3s'},
    {value: CbxSlideshowInterval.FIVE_SECONDS, label: '5s'},
    {value: CbxSlideshowInterval.TEN_SECONDS, label: '10s'},
    {value: CbxSlideshowInterval.FIFTEEN_SECONDS, label: '15s'},
    {value: CbxSlideshowInterval.THIRTY_SECONDS, label: '30s'}
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

  get isLongStrip(): boolean {
    return this.state.scrollMode === CbxScrollMode.LONG_STRIP;
  }

  get isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  get currentScrollModeLabel(): string {
    return this.scrollModeOptions.find(o => o.value === this.state.scrollMode)?.label || 'Paginated';
  }

  get currentSlideshowIntervalLabel(): string {
    return this.slideshowIntervalOptions.find(o => o.value === this.state.slideshowInterval)?.label || '5s';
  }

  onFitModeSelect(mode: CbxFitMode): void {
    this.quickSettingsService.emitFitModeChange(mode);
  }

  onScrollModeSelect(mode: CbxScrollMode): void {
    this.quickSettingsService.emitScrollModeChange(mode);
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

  onReadingDirectionToggle(): void {
    const newDirection = this.state.readingDirection === CbxReadingDirection.LTR
      ? CbxReadingDirection.RTL
      : CbxReadingDirection.LTR;
    this.quickSettingsService.emitReadingDirectionChange(newDirection);
  }

  onSlideshowIntervalSelect(interval: CbxSlideshowInterval): void {
    this.quickSettingsService.emitSlideshowIntervalChange(interval);
  }

  onOverlayClick(): void {
    this.quickSettingsService.close();
  }
}
