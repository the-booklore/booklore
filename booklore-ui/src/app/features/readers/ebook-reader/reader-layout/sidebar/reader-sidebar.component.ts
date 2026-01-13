import {Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BookMark} from '../../../../../shared/service/book-mark.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';

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
  @Input() chapters: { label: string; href: string }[] = [];
  @Input() bookmarks: BookMark[] = [];
  @Output() close = new EventEmitter<void>();
  @Output() chapterClick = new EventEmitter<string>();
  @Output() bookmarkClick = new EventEmitter<string>();
  @Output() deleteBookmark = new EventEmitter<number>();
  @Output() coverClick = new EventEmitter<void>();

  private urlHelperService = inject(UrlHelperService);

  activeTab: 'chapters' | 'bookmarks' | 'annotation' = 'chapters';
  closing = false;
  bookCoverUrl: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] || changes['coverUpdatedOn']) {
      this.bookCoverUrl = this.bookId
        ? this.urlHelperService.getThumbnailUrl(this.bookId, this.coverUpdatedOn)
        : null;
    }
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
}
