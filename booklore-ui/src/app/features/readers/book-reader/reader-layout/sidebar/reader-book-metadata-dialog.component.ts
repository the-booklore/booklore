import {Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Book} from '../../../../book/model/book.model';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';

@Component({
  selector: 'app-reader-book-metadata-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reader-book-metadata-dialog.component.html',
  styleUrls: ['./reader-book-metadata-dialog.component.scss']
})
export class ReaderBookMetadataDialogComponent {
  @Input() book: Book | null = null;
  @Output() close = new EventEmitter<void>();

  private urlHelperService = inject(UrlHelperService);

  get metadata() {
    return this.book?.metadata;
  }

  get bookCoverUrl(): string | null {
    if (!this.book?.id) return null;
    const coverUpdatedOn = this.book.metadata?.coverUpdatedOn;
    return this.urlHelperService.getCoverUrl(this.book.id, coverUpdatedOn);
  }

  formatDate(date: string | undefined): string {
    if (!date) return 'N/A';
    try {
      return new Date(date).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      });
    } catch {
      return date;
    }
  }

  formatAuthors(authors: string[] | undefined): string {
    if (!authors || authors.length === 0) return 'Unknown';
    return authors.join(', ');
  }

  formatFileSize(sizeKb: number | undefined): string {
    if (!sizeKb) return 'N/A';
    if (sizeKb < 1024) return `${sizeKb.toFixed(1)} KB`;
    return `${(sizeKb / 1024).toFixed(2)} MB`;
  }
}
