import {Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, CbxReadingDirection, CbxSlideshowInterval, PdfBackgroundColor, PdfFitMode, PdfPageSpread, PdfPageViewMode, PdfScrollMode} from '../../../../settings/user-management/user.service';

export interface CbxQuickSettingsState {
  fitMode: CbxFitMode | PdfFitMode;
  scrollMode: CbxScrollMode | PdfScrollMode;
  pageViewMode: CbxPageViewMode | PdfPageViewMode;
  pageSpread: CbxPageSpread | PdfPageSpread;
  backgroundColor: CbxBackgroundColor | PdfBackgroundColor;
  readingDirection: CbxReadingDirection;
  slideshowInterval: CbxSlideshowInterval;
}

@Injectable()
export class CbxQuickSettingsService {
  private _state = new BehaviorSubject<CbxQuickSettingsState>({
    fitMode: CbxFitMode.FIT_PAGE,
    scrollMode: CbxScrollMode.PAGINATED,
    pageViewMode: CbxPageViewMode.SINGLE_PAGE,
    pageSpread: CbxPageSpread.ODD,
    backgroundColor: CbxBackgroundColor.GRAY,
    readingDirection: CbxReadingDirection.LTR,
    slideshowInterval: CbxSlideshowInterval.FIVE_SECONDS
  });
  state$ = this._state.asObservable();

  private _visible = new BehaviorSubject<boolean>(false);
  visible$ = this._visible.asObservable();

  private _fitModeChange = new Subject<CbxFitMode>();
  fitModeChange$ = this._fitModeChange.asObservable();

  private _scrollModeChange = new Subject<CbxScrollMode>();
  scrollModeChange$ = this._scrollModeChange.asObservable();

  private _pageViewModeChange = new Subject<CbxPageViewMode>();
  pageViewModeChange$ = this._pageViewModeChange.asObservable();

  private _pageSpreadChange = new Subject<CbxPageSpread>();
  pageSpreadChange$ = this._pageSpreadChange.asObservable();

  private _backgroundColorChange = new Subject<CbxBackgroundColor>();
  backgroundColorChange$ = this._backgroundColorChange.asObservable();

  private _readingDirectionChange = new Subject<CbxReadingDirection>();
  readingDirectionChange$ = this._readingDirectionChange.asObservable();

  private _slideshowIntervalChange = new Subject<CbxSlideshowInterval>();
  slideshowIntervalChange$ = this._slideshowIntervalChange.asObservable();

  get state(): CbxQuickSettingsState {
    return this._state.value;
  }

  get isVisible(): boolean {
    return this._visible.value;
  }

  show(): void {
    this._visible.next(true);
  }

  close(): void {
    this._visible.next(false);
  }

  updateState(partial: Partial<CbxQuickSettingsState>): void {
    this._state.next({...this._state.value, ...partial});
  }

  setFitMode(mode: CbxFitMode): void {
    this.updateState({fitMode: mode});
  }

  setScrollMode(mode: CbxScrollMode): void {
    this.updateState({scrollMode: mode});
  }

  setPageViewMode(mode: CbxPageViewMode | PdfPageViewMode): void {
    this.updateState({pageViewMode: mode});
  }

  setPageSpread(spread: CbxPageSpread | PdfPageSpread): void {
    this.updateState({pageSpread: spread});
  }

  setBackgroundColor(color: CbxBackgroundColor): void {
    this.updateState({backgroundColor: color});
  }

  setReadingDirection(direction: CbxReadingDirection): void {
    this.updateState({readingDirection: direction});
  }

  setSlideshowInterval(interval: CbxSlideshowInterval): void {
    this.updateState({slideshowInterval: interval});
  }

  // Actions emitted from component
  emitFitModeChange(mode: CbxFitMode): void {
    this._fitModeChange.next(mode);
  }

  emitScrollModeChange(mode: CbxScrollMode): void {
    this._scrollModeChange.next(mode);
  }

  emitPageViewModeChange(mode: CbxPageViewMode): void {
    this._pageViewModeChange.next(mode);
  }

  emitPageSpreadChange(spread: CbxPageSpread): void {
    this._pageSpreadChange.next(spread);
  }

  emitBackgroundColorChange(color: CbxBackgroundColor): void {
    this._backgroundColorChange.next(color);
  }

  emitReadingDirectionChange(direction: CbxReadingDirection): void {
    this._readingDirectionChange.next(direction);
  }

  emitSlideshowIntervalChange(interval: CbxSlideshowInterval): void {
    this._slideshowIntervalChange.next(interval);
  }

  reset(): void {
    this._state.next({
      fitMode: CbxFitMode.FIT_PAGE,
      scrollMode: CbxScrollMode.PAGINATED,
      pageViewMode: CbxPageViewMode.SINGLE_PAGE,
      pageSpread: CbxPageSpread.ODD,
      backgroundColor: CbxBackgroundColor.GRAY,
      readingDirection: CbxReadingDirection.LTR,
      slideshowInterval: CbxSlideshowInterval.FIVE_SECONDS
    });
    this._visible.next(false);
  }
}
