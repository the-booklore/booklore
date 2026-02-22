import {FormsModule} from "@angular/forms";
import {Button} from "primeng/button";
import {ActivatedRoute, Router} from "@angular/router";
import {AsyncPipe, DecimalPipe, KeyValuePipe, NgClass, NgStyle} from "@angular/common";
import {filter, finalize, map, switchMap, tap} from "rxjs/operators";
import {combineLatest, Observable, Subscription} from "rxjs";
import {Book, BookType, ReadStatus} from "../../model/book.model";
import {BookService} from "../../service/book.service";
import {BookMetadataManageService} from "../../service/book-metadata-manage.service";
import {BookCardComponent} from "../book-browser/book-card/book-card.component";
import {CoverScalePreferenceService} from "../book-browser/cover-scale-preference.service";
import {Tab, TabList, TabPanel, TabPanels, Tabs} from "primeng/tabs";
import {Tag} from "primeng/tag";
import {VirtualScrollerModule} from "@iharbeck/ngx-virtual-scroller";
import {ProgressSpinner} from "primeng/progressspinner";
import {ProgressBar} from "primeng/progressbar";
import {DynamicDialogRef} from "primeng/dynamicdialog";
import {ConfirmationService, MenuItem, MessageService} from "primeng/api";
import {UserService} from "../../../settings/user-management/user.service";
import {BookMenuService} from "../../service/book-menu.service";
import {LoadingService} from "../../../../core/services/loading.service";
import {BookDialogHelperService} from "../book-browser/book-dialog-helper.service";
import {TaskHelperService} from "../../../settings/task-management/task-helper.service";
import {MetadataRefreshType} from "../../../metadata/model/request/metadata-refresh-type.enum";
import {TieredMenu} from "primeng/tieredmenu";
import {AppSettingsService} from "../../../../shared/service/app-settings.service";
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Tooltip} from "primeng/tooltip";
import {Divider} from "primeng/divider";
import {TagComponent} from "../../../../shared/components/tag/tag.component";
import {animate, style, transition, trigger} from "@angular/animations";
import {AfterViewChecked, Component, ElementRef, inject, OnDestroy, ViewChild} from '@angular/core';
import {BookCardOverlayPreferenceService} from '../book-browser/book-card-overlay-preference.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';

interface ReadStatusSegment {
  status: ReadStatus;
  count: number;
  percent: number;
}

interface SeriesCompletion {
  owned: number;
  total: number;
  percent: number;
  missingNumbers: number[];
}

interface NextUpBook {
  book: Book;
  thumbnailUrl: string;
  isReading: boolean;
  progressPercent: number | null;
}

interface SeriesStats {
  totalPages: number;
  avgPersonalRating: number | null;
  avgGoodreads: number | null;
  avgAmazon: number | null;
  avgHardcover: number | null;
  formatCounts: Map<BookType, number>;
  totalAudioDurationSeconds: number;
}

@Component({
  selector: "app-series-page",
  standalone: true,
  templateUrl: "./series-page.component.html",
  styleUrls: ["./series-page.component.scss"],
  imports: [
    AsyncPipe,
    DecimalPipe,
    KeyValuePipe,
    Button,
    FormsModule,
    NgStyle,
    NgClass,
    BookCardComponent,
    ProgressSpinner,
    ProgressBar,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Tag,
    VirtualScrollerModule,
    TieredMenu,
    Tooltip,
    Divider,
    TranslocoDirective,
    TagComponent
  ],
  animations: [
    trigger('slideInOut', [
      transition(':enter', [
        style({transform: 'translateY(100%)'}),
        animate('0.1s ease-in', style({transform: 'translateY(0)'}))
      ]),
      transition(':leave', [
        style({transform: 'translateY(0)'}),
        animate('0.1s ease-out', style({transform: 'translateY(100%)'}))
      ])
    ])
  ]
})
export class SeriesPageComponent implements OnDestroy, AfterViewChecked {

  private route = inject(ActivatedRoute);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  private metadataCenterViewMode: "route" | "dialog" = "route";
  private dialogRef?: DynamicDialogRef | null;
  private router = inject(Router);
  protected userService = inject(UserService);
  private bookMenuService = inject(BookMenuService);
  protected confirmationService = inject(ConfirmationService);
  private loadingService = inject(LoadingService);
  private dialogHelperService = inject(BookDialogHelperService);
  protected taskHelperService = inject(TaskHelperService);
  private messageService = inject(MessageService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);
  protected urlHelper = inject(UrlHelperService);

  @ViewChild('descriptionContent') descriptionContentRef?: ElementRef<HTMLElement>;
  tab: string = "view";
  isExpanded = false;
  isOverflowing = false;

  // Selection state
  selectedBooks = new Set<number>();
  lastSelectedIndex: number | null = null;
  currentBooks: Book[] = [];
  private userSub: Subscription;

  // Menu items
  protected metadataMenuItems: MenuItem[] | undefined;
  protected moreActionsMenuItems: MenuItem[] | undefined;

  seriesParam$: Observable<string> = this.route.paramMap.pipe(
    map((params) => params.get("seriesName") || ""),
    map((name) => decodeURIComponent(name))
  );

  booksInSeries$: Observable<Book[]> = this.bookService.bookState$.pipe(
    filter((state) => state.loaded && !!state.books),
    map((state) => state.books || [])
  );

  filteredBooks$: Observable<Book[]> = combineLatest([
    this.seriesParam$.pipe(map((n) => n.trim().toLowerCase())),
    this.booksInSeries$,
  ]).pipe(
    map(([seriesName, books]) => {
      const inSeries = books.filter(
        (b) => b.metadata?.seriesName?.toLowerCase() === seriesName
      );
      return inSeries.sort((a, b) => {
        const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        return aNum - bNum;
      });
    }),
    tap(books => this.currentBooks = books)
  );

  coverBook$: Observable<Book | null> = this.filteredBooks$.pipe(
    map(books => books[0] ?? null)
  );

  seriesTitle$: Observable<string> = combineLatest([
    this.seriesParam$,
    this.filteredBooks$,
  ]).pipe(map(([param, books]) => books[0]?.metadata?.seriesName || param));

  yearsRange$: Observable<string | null> = this.filteredBooks$.pipe(
    map((books) => {
      const years = books
        .map((b) => b.metadata?.publishedDate)
        .filter((d): d is string => !!d)
        .map((d) => {
          const match = d.match(/\d{4}/);
          return match ? parseInt(match[0], 10) : null;
        })
        .filter((y): y is number => y !== null);

      if (years.length === 0) return null;
      const min = Math.min(...years);
      const max = Math.max(...years);
      return min === max ? String(min) : `${min}-${max}`;
    })
  );

  firstBookWithDesc$: Observable<Book> = this.filteredBooks$.pipe(
    map((books) => books[0]),
    filter((b): b is Book => !!b),
    switchMap((b) => this.bookService.getBookByIdFromAPI(b.id, true))
  );

  firstDescription$: Observable<string> = this.firstBookWithDesc$.pipe(
    map((b) => b.metadata?.description || "")
  );

  seriesReadStatus$: Observable<ReadStatus> = this.filteredBooks$.pipe(
    map((books) => {
      if (!books || books.length === 0) return ReadStatus.UNREAD;
      const statuses = books.map((b) => (b.readStatus as ReadStatus) ?? ReadStatus.UNREAD);

      const hasWontRead = statuses.includes(ReadStatus.WONT_READ);
      if (hasWontRead) return ReadStatus.WONT_READ;

      const hasAbandoned = statuses.includes(ReadStatus.ABANDONED);
      if (hasAbandoned) return ReadStatus.ABANDONED;

      const allRead = statuses.every((s) => s === ReadStatus.READ);
      if (allRead) return ReadStatus.READ;

      const isAnyReading = statuses.some(
        (s) => s === ReadStatus.READING || s === ReadStatus.RE_READING || s === ReadStatus.PAUSED
      );
      if (isAnyReading) return ReadStatus.READING;

      const someRead = statuses.some((s) => s === ReadStatus.READ);
      if (someRead) return ReadStatus.PARTIALLY_READ;

      const allUnread = statuses.every((s) => s === ReadStatus.UNREAD);
      if (allUnread) return ReadStatus.UNREAD;

      return ReadStatus.PARTIALLY_READ;
    })
  );

  seriesReadProgress$: Observable<string> = this.filteredBooks$.pipe(
    map((books) => {
      const total = books?.length ?? 0;
      const readCount = (books || []).filter((b) => b.readStatus === ReadStatus.READ).length;
      return `${readCount}/${total}`;
    })
  );

  // Aggregated metadata across all books
  allAuthors$: Observable<string[]> = this.filteredBooks$.pipe(
    map(books => {
      const set = new Set<string>();
      for (const book of books) {
        book.metadata?.authors?.forEach(a => set.add(a));
      }
      return Array.from(set);
    })
  );

  allCategories$: Observable<string[]> = this.filteredBooks$.pipe(
    map(books => {
      const set = new Set<string>();
      for (const book of books) {
        book.metadata?.categories?.forEach(c => set.add(c));
      }
      return Array.from(set);
    })
  );

  allPublishers$: Observable<string[]> = this.filteredBooks$.pipe(
    map(books => {
      const set = new Set<string>();
      for (const book of books) {
        if (book.metadata?.publisher) set.add(book.metadata.publisher);
      }
      return Array.from(set);
    })
  );

  allLanguages$: Observable<string[]> = this.filteredBooks$.pipe(
    map(books => {
      const set = new Set<string>();
      for (const book of books) {
        if (book.metadata?.language) set.add(book.metadata.language);
      }
      return Array.from(set);
    })
  );

  // Series completion tracker
  seriesCompletion$: Observable<SeriesCompletion | null> = this.filteredBooks$.pipe(
    map(books => {
      const totals = books
        .map(b => b.metadata?.seriesTotal)
        .filter((t): t is number => t != null && t > 0);
      if (totals.length === 0) return null;

      const seriesTotal = Math.max(...totals);
      const ownedNumbers = new Set(
        books
          .map(b => b.metadata?.seriesNumber)
          .filter((n): n is number => n != null)
      );

      const missingNumbers: number[] = [];
      for (let i = 1; i <= seriesTotal; i++) {
        if (!ownedNumbers.has(i)) missingNumbers.push(i);
      }

      const owned = ownedNumbers.size;
      return {
        owned,
        total: seriesTotal,
        percent: seriesTotal > 0 ? Math.round((owned / seriesTotal) * 100) : 0,
        missingNumbers
      };
    })
  );

  // Reading progress breakdown
  readStatusBreakdown$: Observable<ReadStatusSegment[]> = this.filteredBooks$.pipe(
    map(books => {
      if (!books.length) return [];
      const counts = new Map<ReadStatus, number>();
      for (const book of books) {
        const status = (book.readStatus as ReadStatus) ?? ReadStatus.UNREAD;
        counts.set(status, (counts.get(status) || 0) + 1);
      }
      const total = books.length;
      const segments: ReadStatusSegment[] = [];
      for (const [status, count] of counts) {
        segments.push({status, count, percent: (count / total) * 100});
      }
      return segments;
    })
  );

  // Next up / continue reading
  nextUp$: Observable<NextUpBook | null> = this.filteredBooks$.pipe(
    map(books => {
      const reading = books.find(b =>
        b.readStatus === ReadStatus.READING ||
        b.readStatus === ReadStatus.RE_READING ||
        b.readStatus === ReadStatus.PAUSED
      );
      const target = reading || books.find(b => b.readStatus === ReadStatus.UNREAD || !b.readStatus);
      if (!target) return null;

      const isAudiobook = target.primaryFile?.bookType === 'AUDIOBOOK';
      const thumbnailUrl = isAudiobook
        ? this.urlHelper.getAudiobookThumbnailUrl(target.id, target.metadata?.audiobookCoverUpdatedOn)
        : this.urlHelper.getThumbnailUrl(target.id, target.metadata?.coverUpdatedOn);

      const isReading = target.readStatus === ReadStatus.READING ||
        target.readStatus === ReadStatus.RE_READING ||
        target.readStatus === ReadStatus.PAUSED;

      let progressPercent: number | null = null;
      if (isReading) {
        progressPercent = target.epubProgress?.percentage
          ?? target.pdfProgress?.percentage
          ?? target.cbxProgress?.percentage
          ?? target.audiobookProgress?.percentage
          ?? target.koreaderProgress?.percentage
          ?? target.koboProgress?.percentage
          ?? null;
        if (progressPercent != null) {
          progressPercent = Math.round(progressPercent);
        }
      }

      return {book: target, thumbnailUrl, isReading, progressPercent};
    })
  );

  // Series statistics
  seriesStats$: Observable<SeriesStats> = this.filteredBooks$.pipe(
    map(books => {
      let totalPages = 0;
      const personalRatings: number[] = [];
      const goodreadsRatings: number[] = [];
      const amazonRatings: number[] = [];
      const hardcoverRatings: number[] = [];
      const formatCounts = new Map<BookType, number>();
      let totalAudioDurationSeconds = 0;

      for (const book of books) {
        if (book.metadata?.pageCount) totalPages += book.metadata.pageCount;
        if (book.personalRating != null) personalRatings.push(book.personalRating);
        if (book.metadata?.goodreadsRating != null) goodreadsRatings.push(book.metadata.goodreadsRating);
        if (book.metadata?.amazonRating != null) amazonRatings.push(book.metadata.amazonRating);
        if (book.metadata?.hardcoverRating != null) hardcoverRatings.push(book.metadata.hardcoverRating);

        const bookType = book.primaryFile?.bookType;
        if (bookType) {
          formatCounts.set(bookType, (formatCounts.get(bookType) || 0) + 1);
        }

        if (book.metadata?.audiobookMetadata?.durationSeconds) {
          totalAudioDurationSeconds += book.metadata.audiobookMetadata.durationSeconds;
        }
      }

      const avg = (arr: number[]) => arr.length > 0 ? arr.reduce((a, b) => a + b, 0) / arr.length : null;

      return {
        totalPages,
        avgPersonalRating: avg(personalRatings),
        avgGoodreads: avg(goodreadsRatings),
        avgAmazon: avg(amazonRatings),
        avgHardcover: avg(hardcoverRatings),
        formatCounts,
        totalAudioDurationSeconds
      };
    })
  );

  constructor() {
    this.userSub = this.userService.userState$.pipe(filter(u => !!u?.user && u.loaded))
      .subscribe(userState => {
        this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
          () => this.autoFetchMetadata(),
          () => this.fetchMetadata(),
          () => this.bulkEditMetadata(),
          () => this.multiBookEditMetadata(),
          () => this.regenerateCoversForSelected(),
          () => this.generateCustomCoversForSelected(),
          userState.user
        );
        this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
      });
  }

  ngOnDestroy(): void {
    this.userSub.unsubscribe();
  }

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  goToAuthorBooks(author: string): void {
    this.handleMetadataClick("author", author);
  }

  goToCategory(category: string): void {
    this.handleMetadataClick("category", category);
  }

  goToPublisher(publisher: string): void {
    this.handleMetadataClick("publisher", publisher);
  }

  private navigateToFilteredBooks(
    filterKey: string,
    filterValue: string
  ): void {
    this.router.navigate(["/all-books"], {
      queryParams: {
        view: "grid",
        sort: "title",
        direction: "asc",
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`,
      },
    });
  }

  private handleMetadataClick(filterKey: string, filterValue: string): void {
    if (this.metadataCenterViewMode === "dialog") {
      this.dialogRef?.close();
      setTimeout(
        () => this.navigateToFilteredBooks(filterKey, filterValue),
        200
      );
    } else {
      this.navigateToFilteredBooks(filterKey, filterValue);
    }
  }

  ngAfterViewChecked(): void {
    if (!this.isExpanded && this.descriptionContentRef) {
      const el = this.descriptionContentRef.nativeElement;
      this.isOverflowing = el.scrollHeight > el.clientHeight;
    }
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  getStatusLabel(value: string | ReadStatus | null | undefined): string {
    const v = (value ?? '').toString().toUpperCase();
    switch (v) {
      case ReadStatus.UNREAD:
        return this.t.translate('book.seriesPage.status.unread');
      case ReadStatus.READING:
        return this.t.translate('book.seriesPage.status.reading');
      case ReadStatus.RE_READING:
        return this.t.translate('book.seriesPage.status.reReading');
      case ReadStatus.READ:
        return this.t.translate('book.seriesPage.status.read');
      case ReadStatus.PARTIALLY_READ:
        return this.t.translate('book.seriesPage.status.partiallyRead');
      case ReadStatus.PAUSED:
        return this.t.translate('book.seriesPage.status.paused');
      case ReadStatus.ABANDONED:
        return this.t.translate('book.seriesPage.status.abandoned');
      case ReadStatus.WONT_READ:
        return this.t.translate('book.seriesPage.status.wontRead');
      default:
        return this.t.translate('book.seriesPage.status.unset');
    }
  }

  getStatusColor(status: string | ReadStatus | null | undefined): string {
    const normalized = (status ?? '').toString().toUpperCase();
    switch (normalized) {
      case "UNREAD":
        return "#6b7280";
      case "READING":
        return "#2563eb";
      case "READ":
        return "#16a34a";
      case "PARTIALLY_READ":
        return "#ca8a04";
      case "PAUSED":
        return "#475569";
      case "RE-READING":
      case "RE_READING":
        return "#9333ea";
      case "ABANDONED":
        return "#dc2626";
      case "WONT_READ":
        return "#be185d";
      default:
        return "#4b5563";
    }
  }

  getBookThumbnailUrl(book: Book): string {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  readBook(bookId: number): void {
    this.bookService.readBook(bookId);
  }

  viewBookDetails(bookId: number): void {
    this.router.navigate(['/book', bookId], {
      queryParams: {tab: 'view'}
    });
  }

  getBookProgress(book: Book): number | null {
    const progress = book.epubProgress?.percentage
      ?? book.pdfProgress?.percentage
      ?? book.cbxProgress?.percentage
      ?? book.audiobookProgress?.percentage
      ?? book.koreaderProgress?.percentage
      ?? book.koboProgress?.percentage;
    if (progress == null || progress <= 0) return null;
    const pct = progress > 1 ? progress : Math.round(progress * 100);
    return Math.min(Math.round(pct), 100);
  }

  formatDuration(totalSeconds: number): string {
    if (!totalSeconds || !isFinite(totalSeconds)) return '0:00';
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    if (h > 0) {
      return `${h}h ${m}m`;
    }
    return `${m}m`;
  }

  getRatingPercent(rating: number | null | undefined): number {
    if (rating == null) return 0;
    return Math.round((rating / 5) * 100);
  }

  handleBookSelection(book: Book, selected: boolean) {
    if (selected) {
      if (book.seriesBooks) {
        this.selectedBooks = new Set([...this.selectedBooks, ...book.seriesBooks.map(book => book.id)]);
      } else {
        this.selectedBooks.add(book.id);
      }
    } else {
      if (book.seriesBooks) {
        book.seriesBooks.forEach(book => {
          this.selectedBooks.delete(book.id);
        });
      } else {
        this.selectedBooks.delete(book.id);
      }
    }
  }

  onCheckboxClicked(event: { index: number; book: Book; selected: boolean; shiftKey: boolean }) {
    const {index, book, selected, shiftKey} = event;
    if (!shiftKey || this.lastSelectedIndex === null) {
      this.handleBookSelection(book, selected);
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      const isUnselectingRange = !selected;
      for (let i = start; i <= end; i++) {
        const book = this.currentBooks[i];
        if (!book) continue;
        this.handleBookSelection(book, !isUnselectingRange);
      }
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  handleBookSelect(book: Book, selected: boolean): void {
    this.handleBookSelection(book, selected);
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  selectAllBooks(): void {
    if (!this.currentBooks) return;
    for (const book of this.currentBooks) {
      this.selectedBooks.add(book.id);
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  confirmDeleteBooks(): void {
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.deleteMessage', {count: this.selectedBooks.size}),
      header: this.t.translate('book.browser.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptLabel: this.t.translate('common.delete'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => {
        const count = this.selectedBooks.size;
        const loader = this.loadingService.show(this.t.translate('book.browser.loading.deleting', {count}));

        this.bookService.deleteBooks(this.selectedBooks)
          .pipe(finalize(() => this.loadingService.hide(loader)))
          .subscribe(() => {
            this.selectedBooks.clear();
          });
      },
      reject: () => {
      }
    });
  }

  openShelfAssigner(): void {
    this.dialogRef = this.dialogHelperService.openShelfAssignerDialog(null, this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.subscribe(result => {
        if (result.assigned) {
          this.selectedBooks.clear();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks);
  }

  autoFetchMetadata(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: Array.from(this.selectedBooks),
    }).subscribe();
  }

  fetchMetadata(): void {
    this.dialogHelperService.openMetadataRefreshDialog(this.selectedBooks);
  }

  bulkEditMetadata(): void {
    this.dialogHelperService.openBulkMetadataEditDialog(this.selectedBooks);
  }

  multiBookEditMetadata(): void {
    this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks);
  }

  regenerateCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.regenCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.regenCoverHeader'),
      icon: 'pi pi-image',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.regenerateCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.regenCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  generateCustomCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.customCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.customCoverHeader'),
      icon: 'pi pi-palette',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.generateCustomCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.customCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  moveFiles() {
    this.dialogHelperService.openFileMoverDialog(this.selectedBooks);
  }

  user() {
    return this.userService.getCurrentUser();
  }

  get hasMetadataMenuItems(): boolean {
    return (this.metadataMenuItems?.length ?? 0) > 0;
  }

  get hasMoreActionsItems(): boolean {
    return (this.moreActionsMenuItems?.length ?? 0) > 0;
  }
}
