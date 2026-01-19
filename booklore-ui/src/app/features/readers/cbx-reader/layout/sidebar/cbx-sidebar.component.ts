import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {CbxSidebarService, CbxSidebarTab, SidebarBookInfo} from './cbx-sidebar.service';
import {CbxPageInfo} from '../../../../book/service/cbx-reader.service';
import {PdfOutlineItem} from '../../../../book/service/new-pdf-reader.service';
import {BookMark} from '../../../../../shared/service/book-mark.service';
import {BookNoteV2} from '../../../../../shared/service/book-note-v2.service';
import {ReaderIconComponent} from '../../../ebook-reader';

@Component({
  selector: 'app-cbx-sidebar',
  standalone: true,
  templateUrl: './cbx-sidebar.component.html',
  styleUrls: ['./cbx-sidebar.component.scss'],
  imports: [CommonModule, FormsModule, ReaderIconComponent, DatePipe]
})
export class CbxSidebarComponent implements OnInit, OnDestroy {
  private sidebarService = inject(CbxSidebarService);
  private destroy$ = new Subject<void>();

  isOpen = false;
  closing = false;
  activeTab: CbxSidebarTab = 'pages';
  bookInfo: SidebarBookInfo = { id: null, title: '', authors: '', coverUrl: null };
  pages: CbxPageInfo[] = [];
  pdfOutline: PdfOutlineItem[] | null = null;
  pdfPageCount = 0;
  expandedOutlineItems = new Set<string>();
  currentPage = 1;
  bookmarks: BookMark[] = [];
  notes: BookNoteV2[] = [];
  notesSearchQuery = '';

  ngOnInit(): void {
    this.sidebarService.isOpen$
      .pipe(takeUntil(this.destroy$))
      .subscribe(isOpen => {
        if (isOpen) {
          this.isOpen = true;
          this.closing = false;
        } else if (this.isOpen) {
          this.closeWithAnimation();
        }
      });

    this.sidebarService.activeTab$
      .pipe(takeUntil(this.destroy$))
      .subscribe(tab => this.activeTab = tab);

    this.sidebarService.bookInfo$
      .pipe(takeUntil(this.destroy$))
      .subscribe(info => this.bookInfo = info);

    this.sidebarService.pages$
      .pipe(takeUntil(this.destroy$))
      .subscribe(pages => this.pages = pages);

    this.sidebarService.pdfOutline$
      .pipe(takeUntil(this.destroy$))
      .subscribe(outline => {
        this.pdfOutline = outline;
        this.autoExpandToCurrentPage();
      });

    this.sidebarService.pdfPageCount$
      .pipe(takeUntil(this.destroy$))
      .subscribe(count => this.pdfPageCount = count);

    this.sidebarService.currentPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => {
        this.currentPage = page;
        this.autoExpandToCurrentPage();
      });

    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => this.bookmarks = bookmarks);

    this.sidebarService.notes$
      .pipe(takeUntil(this.destroy$))
      .subscribe(notes => this.notes = notes);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private closeWithAnimation(): void {
    this.closing = true;
    setTimeout(() => {
      this.isOpen = false;
      this.closing = false;
    }, 250);
  }

  onOverlayClick(): void {
    this.sidebarService.close();
  }

  setActiveTab(tab: CbxSidebarTab): void {
    this.sidebarService.setActiveTab(tab);
  }

  onPageClick(pageNumber: number): void {
    this.sidebarService.navigateToPage(pageNumber);
  }

  isPageActive(pageNumber: number): boolean {
    return this.currentPage === pageNumber;
  }

  get isPdf(): boolean {
    return this.sidebarService.isPdf;
  }

  get hasOutline(): boolean {
    return this.pdfOutline !== null && this.pdfOutline.length > 0;
  }

  toggleOutlineItem(item: PdfOutlineItem): void {
    const key = `${item.title}-${item.pageNumber}`;
    if (this.expandedOutlineItems.has(key)) {
      this.expandedOutlineItems.delete(key);
    } else {
      this.expandedOutlineItems.add(key);
    }
  }

  isOutlineItemExpanded(item: PdfOutlineItem): boolean {
    const key = `${item.title}-${item.pageNumber}`;
    return this.expandedOutlineItems.has(key);
  }

  onOutlineItemClick(item: PdfOutlineItem): void {
    this.sidebarService.navigateToPage(item.pageNumber);
  }

  isOutlineItemActive(item: PdfOutlineItem, siblings: PdfOutlineItem[] | null): boolean {
    if (!siblings) return this.currentPage >= item.pageNumber;

    const itemIndex = siblings.findIndex(s => s.title === item.title && s.pageNumber === item.pageNumber);
    const nextSibling = siblings[itemIndex + 1];

    if (nextSibling) {
      return this.currentPage >= item.pageNumber && this.currentPage < nextSibling.pageNumber;
    } else {
      return this.currentPage >= item.pageNumber;
    }
  }

  hasActiveChild(item: PdfOutlineItem): boolean {
    if (!item.children || item.children.length === 0) return false;

    return item.children.some((child, index) => {
      if (this.isOutlineItemActive(child, item.children)) return true;
      if (this.hasActiveChild(child)) return true;
      return false;
    });
  }

  private autoExpandToCurrentPage(): void {
    if (!this.pdfOutline) return;

    const expandParents = (items: PdfOutlineItem[] | null): void => {
      if (!items) return;

      for (const item of items) {
        if (item.children && item.children.length > 0) {
          if (this.hasActiveChild(item) || this.isOutlineItemActive(item, items)) {
            const key = `${item.title}-${item.pageNumber}`;
            this.expandedOutlineItems.add(key);
          }
          expandParents(item.children);
        }
      }
    };

    expandParents(this.pdfOutline);
  }

  onBookmarkClick(cfi: string): void {
    this.sidebarService.navigateToBookmark(cfi);
  }

  onDeleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteBookmark(bookmarkId);
  }

  onNoteClick(cfi: string): void {
    this.sidebarService.navigateToNote(cfi);
  }

  onEditNote(event: MouseEvent, note: BookNoteV2): void {
    event.stopPropagation();
    this.sidebarService.editNote(note);
  }

  onDeleteNote(event: MouseEvent, noteId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteNote(noteId);
  }

  onNotesSearchInput(query: string): void {
    this.notesSearchQuery = query;
    this.sidebarService.setNotesSearchQuery(query);
  }

  clearNotesSearch(): void {
    this.notesSearchQuery = '';
    this.sidebarService.setNotesSearchQuery('');
  }

  get filteredNotes(): BookNoteV2[] {
    return this.sidebarService.getFilteredNotes();
  }
}
