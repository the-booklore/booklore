import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {BookService} from '../../../service/book.service';
import {Book, BookMetadata} from '../../../model/book.model';
import {MessageService} from 'primeng/api';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';

@Component({
  selector: 'app-metadata-restore-dialog-component',
  standalone: true,
  imports: [Button],
  templateUrl: './metadata-restore-dialog-component.html',
  styleUrl: './metadata-restore-dialog-component.scss'
})
export class MetadataRestoreDialogComponent implements OnInit {

  bookId!: number;
  book!: Book | undefined;
  backupMetadata: BookMetadata | null = null;

  private dynamicDialogConfig = inject(DynamicDialogConfig);
  protected dynamicDialogRef = inject(DynamicDialogRef);
  protected bookService = inject(BookService);
  private messageService = inject(MessageService);
  protected urlHelperService = inject(UrlHelperService);

  ngOnInit(): void {
    this.bookId = this.dynamicDialogConfig.data.bookId;
    this.book = this.bookService.getBookByIdFromState(this.bookId);

    this.bookService.getBackupMetadata(this.bookId).subscribe({
      next: (data) => {
        this.backupMetadata = data;
      },
      error: err => {
        this.messageService.add({
          severity: 'warn',
          summary: 'No Backup Found',
          detail: err?.error?.message || 'Backup metadata could not be retrieved.'
        });
        this.backupMetadata = null;
      }
    });
  }

  onRestore(): void {
    this.bookService.restoreMetadata(this.bookId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Restore Successful',
          detail: `Metadata restored for book ID ${this.bookId}`
        });
        this.dynamicDialogRef.close({ action: 'restore', bookId: this.bookId });
      },
      error: err => {
        const errorMessage = err?.error?.message || err?.message || 'Unknown error';
        this.messageService.add({
          severity: 'error',
          summary: 'Restore Failed',
          detail: `Failed to restore metadata: ${errorMessage}`
        });
      }
    });
  }
}
