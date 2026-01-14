import {Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BookMark} from '../../../../../shared/service/book-mark.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {TocItem} from 'epubjs';

@Component({
  selector: 'app-reader-sidebar',
  standalone: true,
  templateUrl: './reader-sidebar.component.html',
  styleUrls: ['./reader-sidebar.component.scss'],
  imports: [CommonModule]
})
export class ReaderSidebarComponent implements OnChanges {
  @Input() bookId: number | null = null;
  @Input() coverUpdatedOn: string | undefined;
  @Input() bookTitle: string = '';
  @Input() bookAuthors: string = '';
  @Input() chapters: TocItem[] = [];
  @Input() bookmarks: BookMark[] = [];
  @Input() currentChapterHref: string | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() chapterClick = new EventEmitter<string>();
  @Output() bookmarkClick = new EventEmitter<string>();
  @Output() deleteBookmark = new EventEmitter<number>();
  @Output() coverClick = new EventEmitter<void>();

  private urlHelperService = inject(UrlHelperService);

  activeTab: 'chapters' | 'bookmarks' | 'annotation' = 'chapters';
  closing = false;
  bookCoverUrl: string | null = null;

  expandedChapters = new Set<string>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] || changes['coverUpdatedOn']) {
      this.bookCoverUrl = this.bookId
        ? this.urlHelperService.getThumbnailUrl(this.bookId, this.coverUpdatedOn)
        : null;
    }

    if (changes['currentChapterHref']) {
      if (this.currentChapterHref) {
        this.autoExpandCurrentChapter();
      }
    }
  }

  private autoExpandCurrentChapter(): void {
    if (!this.currentChapterHref) return;

    const expandParents = (items: TocItem[], parents: string[] = []): boolean => {
      for (const item of items) {
        const currentParents = [...parents, item.href];

        const normalizedItemHref = item.href.split('#')[0].replace(/^\//, '');
        const normalizedCurrentHref = this.currentChapterHref!.split('#')[0].replace(/^\//, '');

        if (normalizedItemHref === normalizedCurrentHref || item.href === this.currentChapterHref) {
          parents.forEach(parentHref => this.expandedChapters.add(parentHref));
          return true;
        }

        if (item.subitems?.length) {
          if (expandParents(item.subitems, currentParents)) {
            return true;
          }
        }
      }
      return false;
    };

    expandParents(this.chapters);
  }

  private closeWithAnimation(callback?: () => void) {
    this.closing = true;
    setTimeout(() => {
      this.closing = false;
      if (callback) callback();
      this.close.emit();
    }, 250);
  }

  onChapterClick(href: string) {
    this.closeWithAnimation(() => this.chapterClick.emit(href));
  }

  onBookmarkClick(cfi: string) {
    this.closeWithAnimation(() => this.bookmarkClick.emit(cfi));
  }

  onDeleteBookmark(event: MouseEvent, bookmarkId: number) {
    event.stopPropagation();
    this.deleteBookmark.emit(bookmarkId);
  }

  onCoverClick() {
    this.closeWithAnimation(() => this.coverClick.emit());
  }

  onOverlayClick() {
    this.closeWithAnimation();
  }

  toggleChapterExpand(href: string) {
    if (this.expandedChapters.has(href)) {
      this.expandedChapters.delete(href);
    } else {
      this.expandedChapters.add(href);
    }
  }

  isChapterExpanded(href: string): boolean {
    return this.expandedChapters.has(href);
  }

  isChapterActive(href: string): boolean {
    if (!this.currentChapterHref) return false;

    const normalizedItemHref = href.split('#')[0].replace(/^\//, '');
    const normalizedCurrentHref = this.currentChapterHref.split('#')[0].replace(/^\//, '');

    return normalizedItemHref === normalizedCurrentHref || href === this.currentChapterHref;
  }
}
