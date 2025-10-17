import {Component, DestroyRef, inject, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {BookReview, BookReviewService} from './book-review-service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Rating} from 'primeng/rating';
import {Tag} from 'primeng/tag';
import {Button} from 'primeng/button';
import {ConfirmationService, MessageService} from 'primeng/api';
import {UserService} from '../../../settings/user-management/user.service';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {BookService} from '../../service/book.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';

@Component({
  selector: 'app-book-reviews',
  standalone: true,
  imports: [CommonModule, ProgressSpinner, Rating, Tag, Button, FormsModule, Tooltip],
  templateUrl: './book-reviews.component.html',
  styleUrl: './book-reviews.component.scss'
})
export class BookReviewsComponent implements OnInit, OnChanges {
  @Input() bookId!: number;
  @Input() reviews: BookReview[] | undefined = [];
  @Input() active: boolean = false;

  private reviewService = inject(BookReviewService);
  private bookService = inject(BookService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);
  private destroyRef = inject(DestroyRef);

  loading = false;
  hasPermission = false;
  revealedSpoilers = new Set<number>();
  sortAscending = false;
  reviewsLocked = false;
  allSpoilersRevealed = false;
  reviewDownloadEnabled = true;

  ngOnInit(): void {
    this.checkUserPermissions();
    this.subscribeToBookState();
    this.subscribeToAppSettings();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] && changes['bookId'].currentValue) {
      this.loadReviews();
    }
  }

  private subscribeToBookState(): void {
    this.bookService.bookState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => {
        if (state.books && this.bookId) {
          const book = state.books.find(b => b.id === this.bookId);
          this.reviewsLocked = book?.metadata?.reviewsLocked ?? false;
        }
      });
  }

  private loadReviews(): void {
    if (this.loading || !this.bookId) {
      return;
    }

    this.loading = true;
    this.reviewService.getByBookId(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reviews) => {
          this.reviews = this.sortReviewsByDate(reviews || []);
          this.loading = false;
        },
        error: (error) => {
          console.error('Failed to load reviews for bookId', this.bookId, ':', error);
          this.reviews = [];
          this.loading = false;

          this.messageService.add({
            severity: 'error',
            summary: 'Failed to Load Reviews',
            detail: 'Could not load reviews for this book.',
            life: 3000
          });
        }
      });
  }

  fetchNewReviews(): void {
    if (!this.bookId || this.loading || this.reviewsLocked) return;

    this.loading = true;
    this.revealedSpoilers.clear();

    this.reviewService.refreshReviews(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reviews) => {
          this.reviews = this.sortReviewsByDate(reviews || []);
          this.loading = false;
          this.updateSpoilerState();
          this.messageService.add({
            severity: 'success',
            summary: 'Reviews Updated',
            detail: 'Latest reviews have been fetched successfully.',
            life: 3000
          });
        },
        error: (error) => {
          console.error('Failed to fetch new reviews:', error);
          this.loading = false;
          this.messageService.add({
            severity: 'error',
            summary: 'Fetch Failed',
            detail: 'Could not fetch new reviews for this book.',
            life: 3000
          });
        }
      });
  }

  deleteAllReviews(): void {
    if (!this.reviews || this.reviews.length === 0 || this.reviewsLocked) return;

    this.confirmationService.confirm({
      message: `Are you sure you want to delete all ${this.reviews.length} reviews for this book? This action cannot be undone.`,
      header: 'Confirm Delete All',
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        if (!this.bookId) return;

        this.reviewService.deleteAllByBookId(this.bookId).subscribe({
          next: () => {
            this.reviews = [];
            this.revealedSpoilers.clear();
            this.allSpoilersRevealed = false;
            this.messageService.add({
              severity: 'success',
              summary: 'All Reviews Deleted',
              detail: 'All reviews have been successfully deleted.',
              life: 3000
            });
          },
          error: (error) => {
            console.error('Failed to delete all reviews:', error);
            this.messageService.add({
              severity: 'error',
              summary: 'Delete Failed',
              detail: 'Could not delete all reviews.',
              life: 3000
            });
          }
        });
      }
    });
  }

  toggleSpoilerVisibility(): void {
    if (this.allSpoilersRevealed) {
      this.hideAllSpoilers();
    } else {
      this.revealAllSpoilers();
    }
  }

  hideAllSpoilers(): void {
    this.revealedSpoilers.clear();
    this.allSpoilersRevealed = false;
  }

  revealAllSpoilers(): void {
    if (this.reviews) {
      this.reviews.forEach(review => {
        if (review.spoiler && review.id) {
          this.revealedSpoilers.add(review.id);
        }
      });
      this.allSpoilersRevealed = true;
    }
  }

  toggleReviewsLock(): void {
    if (!this.bookId) return;

    const newLockState = !this.reviewsLocked;
    const fieldActions: Record<string, 'LOCK' | 'UNLOCK'> = {
      'reviewsLocked': newLockState ? 'LOCK' : 'UNLOCK'
    };

    this.bookService.toggleFieldLocks([this.bookId], fieldActions)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          const action = newLockState ? 'locked' : 'unlocked';
          this.messageService.add({
            severity: 'info',
            summary: `Reviews ${action.charAt(0).toUpperCase() + action.slice(1)}`,
            detail: newLockState
              ? 'Reviews are now protected from modifications and refreshes.'
              : 'Reviews can now be modified and refreshed.',
            life: 3000
          });
        },
        error: (error) => {
          console.error('Failed to toggle lock status:', error);
          this.messageService.add({
            severity: 'error',
            summary: 'Lock Toggle Failed',
            detail: 'Could not change the lock status for reviews.',
            life: 3000
          });
        }
      });
  }

  toggleSortOrder(): void {
    this.sortAscending = !this.sortAscending;
    if (this.reviews) {
      this.reviews = this.sortReviewsByDate([...this.reviews]);
    }
  }

  private sortReviewsByDate(reviews: BookReview[]): BookReview[] {
    return reviews.sort((a, b) => {
      if (!a.date && !b.date) return 0;
      if (!a.date) return this.sortAscending ? -1 : 1;
      if (!b.date) return this.sortAscending ? 1 : -1;

      const dateA = new Date(a.date).getTime();
      const dateB = new Date(b.date).getTime();

      return this.sortAscending ? dateA - dateB : dateB - dateA;
    });
  }

  private checkUserPermissions(): void {
    this.userService.userState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(userState => {
        this.hasPermission = userState?.user?.permissions?.admin ||
          userState?.user?.permissions?.canEditMetadata || false;
      });
  }

  deleteReview(review: BookReview): void {
    if (!review.id || this.reviewsLocked) return;

    this.confirmationService.confirm({
      message: `Are you sure you want to delete this review by ${review.reviewerName || 'Anonymous'}?`,
      header: 'Confirm Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.reviewService.delete(review.id!).subscribe({
          next: () => {
            this.reviews = this.reviews?.filter(r => r.id !== review.id);
            this.updateSpoilerState();
            this.messageService.add({
              severity: 'success',
              summary: 'Review Deleted',
              detail: 'The review has been successfully deleted.',
              life: 2000
            });
          },
          error: (error) => {
            console.error('Failed to delete review:', error);
            this.messageService.add({
              severity: 'error',
              summary: 'Delete Failed',
              detail: 'Could not delete the review.',
              life: 3000
            });
          }
        });
      }
    });
  }

  revealSpoiler(reviewId: number): void {
    if (reviewId) {
      this.revealedSpoilers.add(reviewId);
      this.updateSpoilerState();
    }
  }

  private updateSpoilerState(): void {
    const spoilerReviews = this.reviews?.filter(r => r.spoiler) || [];
    const revealedSpoilerReviews = spoilerReviews.filter(r => r.id && this.revealedSpoilers.has(r.id));
    this.allSpoilersRevealed = spoilerReviews.length > 0 && spoilerReviews.length === revealedSpoilerReviews.length;
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  isSpoilerRevealed(reviewId: number): boolean {
    return this.revealedSpoilers.has(reviewId);
  }

  private subscribeToAppSettings(): void {
    this.appSettingsService.appSettings$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(settings => {
        this.reviewDownloadEnabled = settings?.metadataPublicReviewsSettings?.downloadEnabled ?? true;
      });
  }

  getProviderSeverity(provider: string): 'success' | 'warn' {
    switch (provider?.toLowerCase()) {
      case 'amazon':
        return 'warn';
      default:
        return 'success';
    }
  }
}
