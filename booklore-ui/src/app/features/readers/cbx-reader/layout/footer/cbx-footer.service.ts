import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {Book} from '../../../../book/model/book.model';

export interface CbxFooterState {
  currentPage: number;
  totalPages: number;
  isTwoPageView: boolean;
  previousBookInSeries: Book | null;
  nextBookInSeries: Book | null;
  hasSeries: boolean;
}

@Injectable()
export class CbxFooterService {
  private _state = new BehaviorSubject<CbxFooterState>({
    currentPage: 0,
    totalPages: 0,
    isTwoPageView: false,
    previousBookInSeries: null,
    nextBookInSeries: null,
    hasSeries: false
  });
  state$ = this._state.asObservable();

  private _forceVisible = new BehaviorSubject<boolean>(false);
  forceVisible$ = this._forceVisible.asObservable();

  private _previousPage = new Subject<void>();
  previousPage$ = this._previousPage.asObservable();

  private _nextPage = new Subject<void>();
  nextPage$ = this._nextPage.asObservable();

  private _goToPage = new Subject<number>();
  goToPage$ = this._goToPage.asObservable();

  private _firstPage = new Subject<void>();
  firstPage$ = this._firstPage.asObservable();

  private _lastPage = new Subject<void>();
  lastPage$ = this._lastPage.asObservable();

  private _previousBook = new Subject<void>();
  previousBook$ = this._previousBook.asObservable();

  private _nextBook = new Subject<void>();
  nextBook$ = this._nextBook.asObservable();

  private _sliderChange = new Subject<number>();
  sliderChange$ = this._sliderChange.asObservable();

  get state(): CbxFooterState {
    return this._state.value;
  }

  get isVisible(): boolean {
    return this._forceVisible.value;
  }

  setForceVisible(visible: boolean): void {
    this._forceVisible.next(visible);
  }

  updateState(partial: Partial<CbxFooterState>): void {
    this._state.next({...this._state.value, ...partial});
  }

  setCurrentPage(page: number): void {
    this.updateState({currentPage: page});
  }

  setTotalPages(total: number): void {
    this.updateState({totalPages: total});
  }

  setTwoPageView(isTwoPage: boolean): void {
    this.updateState({isTwoPageView: isTwoPage});
  }

  setSeriesBooks(previous: Book | null, next: Book | null): void {
    this.updateState({
      previousBookInSeries: previous,
      nextBookInSeries: next
    });
  }

  setHasSeries(hasSeries: boolean): void {
    this.updateState({hasSeries});
  }

  // Navigation actions (called from footer component)
  emitPreviousPage(): void {
    this._previousPage.next();
  }

  emitNextPage(): void {
    this._nextPage.next();
  }

  emitGoToPage(page: number): void {
    this._goToPage.next(page);
  }

  emitFirstPage(): void {
    this._firstPage.next();
  }

  emitLastPage(): void {
    this._lastPage.next();
  }

  emitPreviousBook(): void {
    this._previousBook.next();
  }

  emitNextBook(): void {
    this._nextBook.next();
  }

  emitSliderChange(page: number): void {
    this._sliderChange.next(page);
  }

  reset(): void {
    this._state.next({
      currentPage: 0,
      totalPages: 0,
      isTwoPageView: false,
      previousBookInSeries: null,
      nextBookInSeries: null,
      hasSeries: false
    });
    this._forceVisible.next(false);
  }
}
