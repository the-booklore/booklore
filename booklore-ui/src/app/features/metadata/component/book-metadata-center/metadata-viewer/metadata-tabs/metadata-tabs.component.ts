import {Component, EventEmitter, Input, Output} from '@angular/core';
import {Book, BookRecommendation, BookType, FileInfo} from '../../../../../book/model/book.model';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
import {BookCardLiteComponent} from '../../../../../book/components/book-card-lite/book-card-lite-component';
import {BookReviewsComponent} from '../../../../../book/components/book-reviews/book-reviews.component';
import {BookNotesComponent} from '../../../../../book/components/book-notes/book-notes-component';
import {BookReadingSessionsComponent} from '../../book-reading-sessions/book-reading-sessions.component';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';

export interface ReadEvent {
  bookId: number;
  reader?: 'pdf-streaming' | 'epub-streaming';
  bookType?: BookType;
}

export interface DownloadEvent {
  book: Book;
}

export interface DownloadAdditionalFileEvent {
  book: Book;
  fileId: number;
}

@Component({
  selector: 'app-metadata-tabs',
  standalone: true,
  imports: [
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
    InfiniteScrollDirective,
    BookCardLiteComponent,
    BookReviewsComponent,
    BookNotesComponent,
    BookReadingSessionsComponent,
    Button,
    Tooltip
  ],
  templateUrl: './metadata-tabs.component.html',
  styleUrl: './metadata-tabs.component.scss'
})
export class MetadataTabsComponent {
  @Input() book!: Book;
  @Input() bookInSeries: Book[] = [];
  @Input() recommendedBooks: BookRecommendation[] = [];

  @Output() readBook = new EventEmitter<ReadEvent>();
  @Output() downloadBook = new EventEmitter<DownloadEvent>();
  @Output() downloadFile = new EventEmitter<DownloadAdditionalFileEvent>();

  get defaultTabValue(): number {
    return this.bookInSeries && this.bookInSeries.length > 1 ? 1 : 2;
  }

  read(bookId: number, reader?: 'pdf-streaming' | 'epub-streaming', bookType?: BookType): void {
    this.readBook.emit({ bookId, reader, bookType });
  }

  download(book: Book): void {
    this.downloadBook.emit({ book });
  }

  downloadAdditionalFile(book: Book, fileId: number): void {
    this.downloadFile.emit({ book, fileId });
  }

  getFileSizeInMB(fileInfo: FileInfo | null | undefined): string {
    const sizeKb = fileInfo?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getFileIcon(fileType: string | null): string {
    if (!fileType) return 'pi pi-file';
    switch (fileType.toLowerCase()) {
      case 'pdf':
        return 'pi pi-file-pdf';
      case 'epub':
      case 'mobi':
      case 'azw3':
        return 'pi pi-book';
      case 'cbz':
      case 'cbr':
      case 'cbx':
        return 'pi pi-image';
      default:
        return 'pi pi-file';
    }
  }

  getFileTypeBgColor(fileType: string | null | undefined): string {
    if (!fileType) return 'var(--p-gray-500)';
    const type = fileType.toLowerCase();
    return `var(--book-type-${type}-color, var(--p-gray-500))`;
  }
}
