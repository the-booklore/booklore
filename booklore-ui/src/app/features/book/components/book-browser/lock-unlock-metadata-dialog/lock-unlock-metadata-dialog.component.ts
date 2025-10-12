import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';

import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {BookService} from '../../../service/book.service';
import {Divider} from 'primeng/divider';

@Component({
  selector: 'app-lock-unlock-metadata-dialog',
  standalone: true,
  imports: [
    Button,
    FormsModule,
    Divider
],
  templateUrl: './lock-unlock-metadata-dialog.component.html',
  styleUrl: './lock-unlock-metadata-dialog.component.scss'
})
export class LockUnlockMetadataDialogComponent implements OnInit {
  private bookService = inject(BookService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  fieldLocks: Record<string, boolean | undefined> = {};

  bookIds: Set<number> = this.dynamicDialogConfig.data.bookIds;

  lockableFields: string[] = [
    'titleLocked', 'subtitleLocked', 'publisherLocked', 'publishedDateLocked', 'descriptionLocked',
    'isbn13Locked', 'isbn10Locked', 'asinLocked', 'pageCountLocked', 'thumbnailLocked', 'languageLocked', 'coverLocked',
    'seriesNameLocked', 'seriesNumberLocked', 'seriesTotalLocked', 'authorsLocked', 'categoriesLocked', 'moodsLocked', 'TagsLocked',
    'amazonRatingLocked', 'amazonReviewCountLocked', 'goodreadsRatingLocked', 'goodreadsReviewCountLocked',
    'hardcoverRatingLocked', 'hardcoverReviewCountLocked', 'goodreadsIdLocked', 'hardcoverIdLocked', 'googleIdLocked', 'comicvineIdLocked'
  ];

  fieldLabels: Record<string, string> = {
    titleLocked: 'Title',
    subtitleLocked: 'Subtitle',
    publisherLocked: 'Publisher',
    publishedDateLocked: 'Date',
    descriptionLocked: 'Description',
    isbn13Locked: 'ISBN-13',
    isbn10Locked: 'ISBN-10',
    asinLocked: 'ASIN',
    pageCountLocked: 'Page Count',
    thumbnailLocked: 'Thumbnail',
    languageLocked: 'Language',
    coverLocked: 'Cover',
    seriesNameLocked: 'Series',
    seriesNumberLocked: 'Series #',
    seriesTotalLocked: 'Series Total #',
    authorsLocked: 'Authors',
    categoriesLocked: 'Genres',
    moodsLocked: 'Moods',
    tagsLocked: 'Tags',
    amazonRatingLocked: 'Amazon ★',
    amazonReviewCountLocked: 'Amazon Reviews',
    goodreadsRatingLocked: 'Goodreads ★',
    goodreadsReviewCountLocked: 'Goodreads Reviews',
    hardcoverRatingLocked: 'Hardcover ★',
    hardcoverReviewCountLocked: 'Hardcover Reviews',
    goodreadsIdLocked: 'Goodreads ID',
    hardcoverIdLocked: 'Hardcover ID',
    googleIdLocked: 'Google ID',
    comicvineIdLocked: 'Comicvine ID',
  };

  isSaving = false;

  ngOnInit(): void {
    this.lockableFields.forEach(field => this.fieldLocks[field] = undefined);
  }

  toggleLockAll(action: 'LOCK' | 'UNLOCK'): void {
    const lockState = action === 'LOCK' ? true : false;
    this.lockableFields.forEach(field => {
      this.fieldLocks[field] = lockState;
    });
  }

  getLockLabel(field: string): string {
    const state = this.fieldLocks[field];
    if (state === undefined) return 'Unselected';
    return state ? 'Locked' : 'Unlocked';
  }

  getLockIcon(field: string): string {
    const state = this.fieldLocks[field];
    return state === undefined ? '' : state ? 'pi pi-lock' : 'pi pi-lock-open';
  }

  resetFieldLocks(): void {
    this.lockableFields.forEach(field => {
      this.fieldLocks[field] = undefined;
    });
  }

  cycleLockState(field: string): void {
    const current = this.fieldLocks[field];
    if (current === undefined) {
      this.fieldLocks[field] = true;
    } else if (current) {
      this.fieldLocks[field] = false;
    } else {
      this.fieldLocks[field] = undefined;
    }
  }

  applyFieldLocks(): void {
    const fieldActions: Record<string, 'LOCK' | 'UNLOCK'> = {};
    for (const [field, locked] of Object.entries(this.fieldLocks)) {
      if (locked !== undefined) {
        fieldActions[field] = locked ? 'LOCK' : 'UNLOCK';
      }
    }

    this.isSaving = true;
    this.bookService.toggleFieldLocks(this.bookIds, fieldActions).subscribe({
      next: () => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Field Locks Updated',
          detail: 'Selected metadata fields have been updated successfully.'
        });
        this.dialogRef.close('fields-updated');
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Failed to Update Field Locks',
          detail: 'An error occurred while updating field lock statuses.'
        });
      }
    });
  }
}
