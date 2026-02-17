import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {LibraryService} from '../../../book/service/library.service';
import {forkJoin, Observable, of} from 'rxjs';
import {catchError, distinctUntilChanged, map, shareReplay, switchMap} from 'rxjs/operators';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {BookState} from '../../../book/model/state/book-state.model';
import {Book, BookRecommendation, ReadStatus} from '../../../book/model/book.model';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {TooltipModule} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../magic-shelf/component/magic-shelf-component';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {SortService} from '../../../book/service/sort.service';
import {PageTitleService} from "../../../../shared/service/page-title.service";
import {SortDirection, SortOption} from '../../../book/model/sort.model';

const DEFAULT_MAX_ITEMS = 20;

@Component({
  selector: 'app-main-dashboard',
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    AsyncPipe,
    ProgressSpinner,
    TooltipModule,
    TranslocoDirective
  ],
  standalone: true
})
export class MainDashboardComponent implements OnInit {

  private bookService = inject(BookService);
  private dialogLauncher = inject(DialogLauncherService);
  protected userService = inject(UserService);
  private dashboardConfigService = inject(DashboardConfigService);
  private magicShelfService = inject(MagicShelfService);
  private ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private sortService = inject(SortService);
  private pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  bookState$ = this.bookService.bookState$;
  dashboardConfig$ = this.dashboardConfigService.config$;

  private scrollerBooksCache = new Map<string, Observable<Book[]>>();

  isLibrariesEmpty$: Observable<boolean> = inject(LibraryService).libraryState$.pipe(
    map(state => !state.libraries || state.libraries.length === 0)
  );

  ScrollerType = ScrollerType;

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('dashboard.main.pageTitle'));

    this.dashboardConfig$.subscribe(() => {
      this.scrollerBooksCache.clear();
    });

    this.magicShelfService.shelvesState$.subscribe(() => {
      this.scrollerBooksCache.clear();
    });
  }

  private getLastReadBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book =>
          book.lastReadTime &&
          (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
          this.hasEbookProgress(book)
        );
        books = books.sort((a, b) => {
          const aTime = new Date(a.lastReadTime!).getTime();
          const bTime = new Date(b.lastReadTime!).getTime();
          return bTime - aTime;
        });
        return books.slice(0, maxItems);
      })
    );
  }

  private getLastListenedBooks(maxItems: number): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book =>
          book.lastReadTime &&
          (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
          book.audiobookProgress
        );
        books = books.sort((a, b) => {
          const aTime = new Date(a.lastReadTime!).getTime();
          const bTime = new Date(b.lastReadTime!).getTime();
          return bTime - aTime;
        });
        return books.slice(0, maxItems);
      })
    );
  }

  private hasEbookProgress(book: Book): boolean {
    return !!(book.epubProgress || book.pdfProgress || book.cbxProgress || book.koreaderProgress || book.koboProgress);
  }

  private getLatestAddedBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book => book.addedOn);

        books = books.sort((a, b) => {
          const aTime = new Date(a.addedOn!).getTime();
          const bTime = new Date(b.addedOn!).getTime();
          return bTime - aTime;
        });

        return books.slice(0, maxItems);
      })
    );
  }

  private getRandomBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        const excludedStatuses = new Set<ReadStatus>([
          ReadStatus.READ,
          ReadStatus.PARTIALLY_READ,
          ReadStatus.READING,
          ReadStatus.PAUSED,
          ReadStatus.WONT_READ,
          ReadStatus.ABANDONED
        ]);

        const candidates = (state.books || []).filter(book =>
          !book.readStatus || !excludedStatuses.has(book.readStatus)
        );

        return this.shuffleBooks(candidates, maxItems);
      })
    );
  }

  private getUpNextBooks(maxItems: number, showFirstUnread: boolean = false, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        const books = state.books || [];
        
        // Group books by series
        const seriesMap = new Map<string, Book[]>();
        books.forEach(book => {
          if (book.metadata?.seriesName && book.metadata?.seriesNumber != null) {
            const seriesName = book.metadata.seriesName.toLowerCase().trim();
            if (!seriesMap.has(seriesName)) {
              seriesMap.set(seriesName, []);
            }
            seriesMap.get(seriesName)!.push(book);
          }
        });

        const upNextBooks: Array<{book: Book, lastReadTime: number}> = [];

        // For each series, find the next unread book
        seriesMap.forEach((seriesBooks) => {
          // Check if any book in the series has been read or started
          const hasReadBooks = seriesBooks.some(book =>
            book.readStatus === ReadStatus.READ ||
            book.readStatus === ReadStatus.READING ||
            book.readStatus === ReadStatus.RE_READING ||
            book.readStatus === ReadStatus.PAUSED ||
            book.readStatus === ReadStatus.PARTIALLY_READ
          );

          if (!hasReadBooks) {
            return; // Skip series with no read books
          }

          // Sort by series number
          const sortedBooks = [...seriesBooks].sort((a, b) => {
            const aNum = a.metadata?.seriesNumber ?? 0;
            const bNum = b.metadata?.seriesNumber ?? 0;
            return aNum - bNum;
          });

          // Find the highest series number that has been read or is currently being read
          const highestReadNumber = sortedBooks
            .filter(book =>
              book.readStatus === ReadStatus.READ ||
              book.readStatus === ReadStatus.READING ||
              book.readStatus === ReadStatus.RE_READING ||
              book.readStatus === ReadStatus.PAUSED ||
              book.readStatus === ReadStatus.PARTIALLY_READ
            )
            .reduce((max, book) => {
              const num = book.metadata?.seriesNumber ?? 0;
              return num > max ? num : max;
            }, 0);

          // Find the next unread book based on mode
          const nextBook = showFirstUnread
            ? sortedBooks.find(book => {
                const isUnread = !book.readStatus ||
                  book.readStatus === ReadStatus.UNREAD ||
                  book.readStatus === ReadStatus.UNSET;
                return isUnread;
              })
            : sortedBooks.find(book => {
                const bookNum = book.metadata?.seriesNumber ?? 0;
                const isUnread = !book.readStatus ||
                  book.readStatus === ReadStatus.UNREAD ||
                  book.readStatus === ReadStatus.UNSET;
                return bookNum > highestReadNumber && isUnread;
              });

          if (nextBook) {
            // Get the most recent read time from the series to prioritize
            const lastReadTime = seriesBooks
              .filter(book => book.lastReadTime)
              .map(book => new Date(book.lastReadTime!).getTime())
              .sort((a, b) => b - a)[0] || 0;

            upNextBooks.push({book: nextBook, lastReadTime});
          }
        });

        // Sort by most recently read series first
        upNextBooks.sort((a, b) => b.lastReadTime - a.lastReadTime);

        return upNextBooks.slice(0, maxItems).map(item => item.book);
      })
    );
  }

  private getReadAgainBooks(maxItems: number, sortByFinished: boolean = false): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let readBooks = (state.books || []).filter(book =>
          book.readStatus === ReadStatus.READ
        );

        if (sortByFinished) {
          // Sort by date finished (most recent first)
          readBooks = readBooks
            .filter(book => book.dateFinished)
            .sort((a, b) => {
              const aTime = new Date(a.dateFinished!).getTime();
              const bTime = new Date(b.dateFinished!).getTime();
              return bTime - aTime;
            })
            .slice(0, maxItems);
          return readBooks;
        }

        return this.shuffleBooks(readBooks, maxItems);
      })
    );
  }

  private getMagicShelfBooks(shelfId: number, maxItems?: number, sortBy?: string): Observable<Book[]> {
    return this.magicShelfService.getShelf(shelfId).pipe(
      switchMap((shelf) => {
        if (!shelf) return this.bookService.bookState$.pipe(map(() => []));

        let group: GroupRule;
        try {
          group = JSON.parse(shelf.filterJson);
        } catch (e) {
          console.error('Invalid filter JSON', e);
          return this.bookService.bookState$.pipe(map(() => []));
        }

        return this.bookService.bookState$.pipe(
          map((state: BookState) => {
            const allBooks = state.books || [];
            const filteredBooks = allBooks.filter((book) =>
              this.ruleEvaluatorService.evaluateGroup(book, group, allBooks)
            );

            return maxItems ? filteredBooks.slice(0, maxItems) : filteredBooks;
          })
        );
      })
    );
  }


  private getRecommendationsBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      // Only recompute when read books actually change to avoid spamming the backend
      map((state: BookState) => {
        const readBooks = (state.books || []).filter(book => book.readStatus === ReadStatus.READ);
        // Create a stable key based on read book IDs to detect meaningful changes
        return {
          readBooks,
          readBookIds: readBooks.map(b => b.id).sort().join(',')
        };
      }),
      distinctUntilChanged((prev, curr) => prev.readBookIds === curr.readBookIds),
      switchMap(({readBooks}) => {
        if (readBooks.length === 0) {
          return of([]);
        }

        // Get a pool of recently read books (last 50) and randomly sample from them
        // This ensures different recommendations each time while still prioritizing recent reads
        const recentReadBooks = readBooks
          .filter(book => book.lastReadTime)
          .sort((a, b) => {
            const aTime = new Date(a.lastReadTime!).getTime();
            const bTime = new Date(b.lastReadTime!).getTime();
            return bTime - aTime;
          })
          .slice(0, 50);

        if (recentReadBooks.length === 0) {
          return of([]);
        }

        // Randomly select up to 5 books from the pool to get recommendations from
        const sampledBooks = this.shuffleBooks(recentReadBooks, Math.min(5, recentReadBooks.length));

        const recommendationCalls = sampledBooks.map(book =>
          this.bookService.getBookRecommendations(book.id, 10).pipe(
            // Handle individual call failures gracefully
            catchError(error => {
              console.warn(`Failed to get recommendations for book ${book.id}:`, error);
              return of([]);
            })
          )
        );

        return forkJoin(recommendationCalls).pipe(
          map((results: BookRecommendation[][]) => {
            // Collect all unique unread recommendations
            const recommendationMap = new Map<number, Book>();

            results.forEach(recommendations => {
              recommendations.forEach(rec => {
                const bookId = rec.book.id;
                
                // Skip books that are already read, in progress, or otherwise not truly unread
                if (rec.book.readStatus === ReadStatus.READ ||
                    rec.book.readStatus === ReadStatus.READING ||
                    rec.book.readStatus === ReadStatus.PAUSED ||
                    rec.book.readStatus === ReadStatus.RE_READING ||
                    rec.book.readStatus === ReadStatus.PARTIALLY_READ ||
                    rec.book.readStatus === ReadStatus.WONT_READ ||
                    rec.book.readStatus === ReadStatus.ABANDONED) {
                  return;
                }

                if (!recommendationMap.has(bookId)) {
                  recommendationMap.set(bookId, rec.book);
                }
              });
            });

            const allRecommendations = Array.from(recommendationMap.values());
            return this.shuffleBooks(allRecommendations, maxItems);
          }),
          // Handle overall operation failure
          catchError(error => {
            console.error('Failed to get book recommendations:', error);
            return of([]);
          })
        );
      })
    );
  }

  getBooksForScroller(config: ScrollerConfig): Observable<Book[]> {
    if (!this.scrollerBooksCache.has(config.id)) {
      let books$: Observable<Book[]>;

      switch (config.type) {
        case ScrollerType.LAST_READ:
          books$ = this.getLastReadBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.LAST_LISTENED:
          books$ = this.getLastListenedBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.LATEST_ADDED:
          books$ = this.getLatestAddedBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.RANDOM:
          books$ = this.getRandomBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.UP_NEXT:
          books$ = this.getUpNextBooks(config.maxItems || DEFAULT_MAX_ITEMS, config.upNextShowFirstUnread || false);
          break;
        case ScrollerType.READ_AGAIN:
          books$ = this.getReadAgainBooks(config.maxItems || DEFAULT_MAX_ITEMS, config.readAgainSortByFinished || false);
          break;
        case ScrollerType.RECOMMENDATIONS:
          books$ = this.getRecommendationsBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.MAGIC_SHELF:
          books$ = this.getMagicShelfBooks(config.magicShelfId!, config.maxItems).pipe(
            map(books => {
              if (config.sortField && config.sortDirection) {
                const sortOption = this.createSortOption(config.sortField, config.sortDirection);
                return this.sortService.applySort(books, sortOption);
              }
              return books;
            })
          );
          break;
        default:
          books$ = this.bookService.bookState$.pipe(map(() => []));
      }

      this.scrollerBooksCache.set(config.id, books$.pipe(shareReplay(1)));
    }

    return this.scrollerBooksCache.get(config.id)!;
  }

  private createSortOption(field: string, direction: string): SortOption {
    return {
      field: field,
      direction: direction === 'asc' ? SortDirection.ASCENDING : SortDirection.DESCENDING,
      label: ''
    };
  }

  private shuffleBooks(books: Book[], maxItems: number): Book[] {
    const shuffled = [...books];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled.slice(0, maxItems);
  }

  openDashboardSettings(): void {
    this.dialogLauncher.openDashboardSettingsDialog();
  }

  createNewLibrary() {
    this.dialogLauncher.openLibraryCreateDialog();
  }
}