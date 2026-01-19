import {inject, Injectable} from '@angular/core';
import {Location} from '@angular/common';
import {BehaviorSubject, Subject} from 'rxjs';
import {CbxSidebarService} from '../sidebar/cbx-sidebar.service';

@Injectable()
export class CbxHeaderService {
  private sidebarService = inject(CbxSidebarService);
  private location = inject(Location);

  private destroy$ = new Subject<void>();
  private bookId!: number;
  private bookTitle = '';

  private _forceVisible = new BehaviorSubject<boolean>(true);
  forceVisible$ = this._forceVisible.asObservable();

  private _showQuickSettings = new Subject<void>();
  showQuickSettings$ = this._showQuickSettings.asObservable();

  private _toggleBookmark = new Subject<void>();
  toggleBookmark$ = this._toggleBookmark.asObservable();

  private _openNoteDialog = new Subject<void>();
  openNoteDialog$ = this._openNoteDialog.asObservable();

  get title(): string {
    return this.bookTitle;
  }

  get isVisible(): boolean {
    return this._forceVisible.value;
  }

  initialize(bookId: number, title: string | undefined, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.bookTitle = title || '';
    this.destroy$ = destroy$;
  }

  setForceVisible(visible: boolean): void {
    this._forceVisible.next(visible);
  }

  openSidebar(): void {
    this.sidebarService.open();
  }

  openQuickSettings(): void {
    this._showQuickSettings.next();
  }

  toggleBookmark(): void {
    this._toggleBookmark.next();
  }

  openNoteDialog(): void {
    this._openNoteDialog.next();
  }

  close(): void {
    this.location.back();
  }

  reset(): void {
    this._forceVisible.next(true);
    this.bookTitle = '';
  }
}
