import {inject, Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {BookMarkService} from '../../../../shared/service/book-mark.service';
import {MessageService} from 'primeng/api';

@Injectable()
export class ReaderBookmarkService {
  private currentCFI: string | null = null;
  private currentChapterName: string | null = null;

  private bookMarkService = inject(BookMarkService);
  private messageService = inject(MessageService);


  updateCurrentPosition(cfi: string, chapterName?: string): void {
    this.currentCFI = cfi;
    if (chapterName) {
      this.currentChapterName = chapterName;
    }
  }

  createBookmarkAtCurrentPosition(bookId: number): Observable<boolean> {
    const cfi = this.currentCFI;
    if (!cfi) {
      console.error('Could not get current CFI - please navigate to a page first');
      return of(false);
    }

    const title = this.currentChapterName || 'Bookmark';

    return this.bookMarkService.createBookmark({bookId, cfi, title}).pipe(
      map(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Bookmark Added',
          detail: 'Your bookmark was added successfully.'
        });
        return true;
      }),
      catchError(error => {
        const isDuplicate = error?.status === 409;
        this.messageService.add(
          isDuplicate
            ? {
              severity: 'warn',
              summary: 'Bookmark Already Exists',
              detail: 'You already have a bookmark at this location.'
            }
            : {
              severity: 'error',
              summary: 'Unable to Add Bookmark',
              detail: 'Something went wrong while adding the bookmark. Please try again.'
            }
        );
        return of(false);
      })
    );
  }

  reset(): void {
    this.currentCFI = null;
    this.currentChapterName = null;
  }
}
