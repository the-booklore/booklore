import {Component, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {CommonModule, Location} from '@angular/common';
import {forkJoin, Subject} from 'rxjs';
import {filter, first, takeUntil, timeout} from 'rxjs/operators';
import {PageTitleService} from "../../../shared/service/page-title.service";
import {CbxReaderService} from '../../book/service/cbx-reader.service';
import {BookService} from '../../book/service/book.service';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, PdfPageSpread, PdfPageViewMode, UserService} from '../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {Book, BookSetting, BookType} from '../../book/model/book.model';
import {BookState} from '../../book/model/state/book-state.model';
import {ProgressSpinner} from 'primeng/progressspinner';
import {FormsModule} from "@angular/forms";
import {NewPdfReaderService} from '../../book/service/new-pdf-reader.service';
import {ReadingSessionService} from '../../../shared/service/reading-session.service';
import {ReaderHeaderFooterVisibilityManager} from '../ebook-reader/shared/visibility.util';

// Layout components
import {CbxHeaderComponent} from './layout/header/cbx-header.component';
import {CbxHeaderService} from './layout/header/cbx-header.service';
import {CbxSidebarComponent} from './layout/sidebar/cbx-sidebar.component';
import {CbxSidebarService} from './layout/sidebar/cbx-sidebar.service';
import {CbxFooterComponent} from './layout/footer/cbx-footer.component';
import {CbxFooterService} from './layout/footer/cbx-footer.service';
import {CbxQuickSettingsComponent} from './layout/quick-settings/cbx-quick-settings.component';
import {CbxQuickSettingsService} from './layout/quick-settings/cbx-quick-settings.service';
import {CbxNoteDialogComponent, CbxNoteDialogData, CbxNoteDialogResult} from './dialogs/cbx-note-dialog.component';
import {BookNoteV2} from '../../../shared/service/book-note-v2.service';


@Component({
  selector: 'app-cbx-reader',
  standalone: true,
  imports: [
    CommonModule,
    ProgressSpinner,
    FormsModule,
    CbxHeaderComponent,
    CbxSidebarComponent,
    CbxFooterComponent,
    CbxQuickSettingsComponent,
    CbxNoteDialogComponent
  ],
  providers: [
    CbxHeaderService,
    CbxSidebarService,
    CbxFooterService,
    CbxQuickSettingsService
  ],
  templateUrl: './cbx-reader.component.html',
  styleUrl: './cbx-reader.component.scss'
})
export class CbxReaderComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  bookType!: BookType;
  bookId!: number;
  pages: number[] = [];
  currentPage = 0;
  isLoading = true;

  pageSpread: CbxPageSpread | PdfPageSpread = CbxPageSpread.ODD;
  pageViewMode: CbxPageViewMode | PdfPageViewMode = CbxPageViewMode.SINGLE_PAGE;
  backgroundColor: CbxBackgroundColor = CbxBackgroundColor.GRAY;
  fitMode: CbxFitMode = CbxFitMode.FIT_PAGE;
  scrollMode: CbxScrollMode = CbxScrollMode.PAGINATED;

  private touchStartX = 0;
  private touchEndX = 0;

  currentBook: Book | null = null;
  nextBookInSeries: Book | null = null;
  previousBookInSeries: Book | null = null;

  infiniteScrollPages: number[] = [];
  preloadCount: number = 3;
  isLoadingMore: boolean = false;

  private visibilityManager!: ReaderHeaderFooterVisibilityManager;

  // Bookmark and notes state
  isCurrentPageBookmarked = false;
  currentPageHasNotes = false;
  showNoteDialog = false;
  noteDialogData: CbxNoteDialogData | null = null;
  private editingNote: BookNoteV2 | null = null;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private cbxReaderService = inject(CbxReaderService);
  private pdfReaderService = inject(NewPdfReaderService);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private pageTitle = inject(PageTitleService);
  private readingSessionService = inject(ReadingSessionService);
  private headerService = inject(CbxHeaderService);
  private sidebarService = inject(CbxSidebarService);
  private footerService = inject(CbxFooterService);
  private quickSettingsService = inject(CbxQuickSettingsService);

  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;

  private static readonly TYPE_PDF = 'PDF';
  private static readonly TYPE_CBX = 'CBX';
  private static readonly SETTING_GLOBAL = 'Global';

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerService.setForceVisible(state.headerVisible);
      this.footerService.setForceVisible(state.footerVisible);
    });

    this.subscribeToHeaderEvents();
    this.subscribeToSidebarEvents();
    this.subscribeToFooterEvents();
    this.subscribeToQuickSettingsEvents();

    this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      this.bookId = +params.get('bookId')!;

      this.previousBookInSeries = null;
      this.nextBookInSeries = null;
      this.currentBook = null;

      forkJoin([
        this.bookService.getBookByIdFromAPI(this.bookId, false),
        this.bookService.getBookSetting(this.bookId),
        this.userService.getMyself()
      ]).subscribe({
        next: ([book, bookSettings, myself]) => {
          const userSettings = myself.userSettings;
          this.bookType = book.bookType;
          this.currentBook = book;

          this.pageTitle.setBookPageTitle(book);

          // Initialize header and sidebar services
          const title = book.metadata?.title || book.fileName;
          this.headerService.initialize(this.bookId, title, this.destroy$);
          this.sidebarService.initialize(this.bookId, book, this.destroy$);

          if (book.metadata?.seriesName) {
            this.loadSeriesNavigationAsync(book);
          }

          const pagesObservable = this.bookType === CbxReaderComponent.TYPE_PDF
            ? this.pdfReaderService.getAvailablePages(this.bookId)
            : this.cbxReaderService.getAvailablePages(this.bookId);

          pagesObservable.subscribe({
            next: (pages) => {
              this.pages = pages;
              if (this.bookType === CbxReaderComponent.TYPE_CBX) {
                const global = userSettings.perBookSetting.cbx === CbxReaderComponent.SETTING_GLOBAL;
                this.pageViewMode = global
                  ? this.CbxPageViewMode[userSettings.cbxReaderSetting.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode.SINGLE_PAGE
                  : this.CbxPageViewMode[bookSettings.cbxSettings?.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode[userSettings.cbxReaderSetting.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode.SINGLE_PAGE;

                this.pageSpread = global
                  ? this.CbxPageSpread[userSettings.cbxReaderSetting.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread.ODD
                  : this.CbxPageSpread[bookSettings.cbxSettings?.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread[userSettings.cbxReaderSetting.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread.ODD;

                this.fitMode = global
                  ? this.CbxFitMode[userSettings.cbxReaderSetting.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode.FIT_PAGE
                  : this.CbxFitMode[bookSettings.cbxSettings?.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode[userSettings.cbxReaderSetting.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode.FIT_PAGE;

                this.scrollMode = global
                  ? this.CbxScrollMode[userSettings.cbxReaderSetting.scrollMode as keyof typeof CbxScrollMode] || CbxScrollMode.PAGINATED
                  : this.CbxScrollMode[bookSettings.cbxSettings?.scrollMode as keyof typeof CbxScrollMode] || this.CbxScrollMode[userSettings.cbxReaderSetting.scrollMode as keyof typeof CbxScrollMode] || CbxScrollMode.PAGINATED;

                this.backgroundColor = global
                  ? this.CbxBackgroundColor[userSettings.cbxReaderSetting.backgroundColor as keyof typeof CbxBackgroundColor] || CbxBackgroundColor.GRAY
                  : this.CbxBackgroundColor[bookSettings.cbxSettings?.backgroundColor as keyof typeof CbxBackgroundColor] || this.CbxBackgroundColor[userSettings.cbxReaderSetting.backgroundColor as keyof typeof CbxBackgroundColor] || CbxBackgroundColor.GRAY;

                this.currentPage = (book.cbxProgress?.page || 1) - 1;

                if (this.scrollMode === CbxScrollMode.INFINITE) {
                  this.initializeInfiniteScroll();
                }
              }

              if (this.bookType === CbxReaderComponent.TYPE_PDF) {
                const global = userSettings.perBookSetting.pdf === CbxReaderComponent.SETTING_GLOBAL;
                this.pageViewMode = global
                  ? PdfPageViewMode[userSettings.newPdfReaderSetting.pageViewMode as keyof typeof PdfPageViewMode] || PdfPageViewMode.SINGLE_PAGE
                  : PdfPageViewMode[bookSettings.newPdfSettings?.pageViewMode as keyof typeof PdfPageViewMode] || PdfPageViewMode[userSettings.newPdfReaderSetting.pageViewMode as keyof typeof PdfPageViewMode] || PdfPageViewMode.SINGLE_PAGE;

                this.pageSpread = global
                  ? PdfPageSpread[userSettings.newPdfReaderSetting.pageSpread as keyof typeof PdfPageSpread] || PdfPageSpread.ODD
                  : PdfPageSpread[bookSettings.newPdfSettings?.pageSpread as keyof typeof PdfPageSpread] || PdfPageSpread[userSettings.newPdfReaderSetting.pageSpread as keyof typeof PdfPageSpread] || PdfPageSpread.ODD;

                this.currentPage = (book.pdfProgress?.page || 1) - 1;
              }
              this.alignCurrentPageToParity();
              this.updateServiceStates();
              this.updateBookmarkState();
              this.updateNotesState();
              this.isLoading = false;

              const percentage = this.pages.length > 0 ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10 : 0;
              this.readingSessionService.startSession(this.bookId, "CBX", (this.currentPage + 1).toString(), percentage);
            },
            error: (err) => {
              const errorMessage = err?.error?.message || 'Failed to load pages';
              this.messageService.add({severity: 'error', summary: 'Error', detail: errorMessage});
              this.isLoading = false;
            }
          });
        },
        error: (err) => {
          const errorMessage = err?.error?.message || 'Failed to load the book';
          this.messageService.add({severity: 'error', summary: 'Error', detail: errorMessage});
          this.isLoading = false;
        }
      });
    });
  }

  private subscribeToHeaderEvents(): void {
    this.headerService.showQuickSettings$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.quickSettingsService.show();
      });

    this.headerService.toggleBookmark$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.toggleBookmark();
      });

    this.headerService.openNoteDialog$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.openNoteDialog();
      });
  }

  private subscribeToSidebarEvents(): void {
    this.sidebarService.navigateToPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => {
        this.goToPage(page);
      });

    this.sidebarService.bookmarksChanged$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateBookmarkState();
      });

    // Subscribe to bookmarks$ to update state when bookmarks are loaded
    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateBookmarkState();
      });

    // Subscribe to notes$ to update state when notes are loaded
    this.sidebarService.notes$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateNotesState();
      });

    this.sidebarService.editNote$
      .pipe(takeUntil(this.destroy$))
      .subscribe(note => {
        this.openNoteDialogForEdit(note);
      });
  }

  private subscribeToFooterEvents(): void {
    this.footerService.previousPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.previousPage());

    this.footerService.nextPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.nextPage());

    this.footerService.goToPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => this.goToPage(page));

    this.footerService.firstPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.firstPage());

    this.footerService.lastPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.lastPage());

    this.footerService.previousBook$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.navigateToPreviousBook());

    this.footerService.nextBook$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.navigateToNextBook());

    this.footerService.sliderChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => this.goToPage(page));
  }

  private subscribeToQuickSettingsEvents(): void {
    this.quickSettingsService.fitModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onFitModeChange(mode));

    this.quickSettingsService.scrollModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onScrollModeChange(mode));

    this.quickSettingsService.pageViewModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onPageViewModeChange(mode));

    this.quickSettingsService.pageSpreadChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(spread => this.onPageSpreadChange(spread));

    this.quickSettingsService.backgroundColorChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(color => this.onBackgroundColorChange(color));
  }

  private updateServiceStates(): void {
    // Update footer state
    this.footerService.updateState({
      currentPage: this.currentPage,
      totalPages: this.pages.length,
      isTwoPageView: this.isTwoPageView,
      previousBookInSeries: this.previousBookInSeries,
      nextBookInSeries: this.nextBookInSeries,
      hasSeries: this.hasSeries
    });

    // Update quick settings state
    this.quickSettingsService.updateState({
      fitMode: this.fitMode,
      scrollMode: this.scrollMode,
      pageViewMode: this.pageViewMode,
      pageSpread: this.pageSpread,
      backgroundColor: this.backgroundColor
    });

    // Update sidebar current page
    this.sidebarService.setCurrentPage(this.currentPage + 1);
  }

  private updateFooterPage(): void {
    this.footerService.setCurrentPage(this.currentPage);
    this.sidebarService.setCurrentPage(this.currentPage + 1);
    this.updateBookmarkState();
    this.updateNotesState();
  }

  get isTwoPageView(): boolean {
    return this.pageViewMode === this.CbxPageViewMode.TWO_PAGE || this.pageViewMode === PdfPageViewMode.TWO_PAGE;
  }

  get hasSeries(): boolean {
    return !!this.currentBook?.metadata?.seriesName;
  }

  get showQuickSettings(): boolean {
    return this.quickSettingsService.isVisible;
  }

  nextPage() {
    const previousPage = this.currentPage;

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      if (this.currentPage < this.pages.length - 1) {
        this.currentPage++;
        this.scrollToPage(this.currentPage);
        this.updateProgress();
        this.updateSessionProgress();
        this.updateFooterPage();
      }
      return;
    }

    if (this.isTwoPageView) {
      if (this.currentPage + 2 < this.pages.length) {
        this.currentPage += 2;
      } else if (this.currentPage + 1 < this.pages.length) {
        this.currentPage += 1;
      }
    } else if (this.currentPage < this.pages.length - 1) {
      this.currentPage++;
    }

    if (this.currentPage !== previousPage) {
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    }
  }

  previousPage() {
    if (this.scrollMode === CbxScrollMode.INFINITE) {
      if (this.currentPage > 0) {
        this.currentPage--;
        this.scrollToPage(this.currentPage);
        this.updateProgress();
        this.updateSessionProgress();
        this.updateFooterPage();
      }
      return;
    }

    if (this.isTwoPageView) {
      this.currentPage = Math.max(0, this.currentPage - 2);
    } else {
      this.currentPage = Math.max(0, this.currentPage - 1);
    }
    this.updateProgress();
    this.updateSessionProgress();
    this.updateFooterPage();
  }

  private alignCurrentPageToParity() {
    if (!this.pages.length || !this.isTwoPageView) return;

    const desiredOdd = this.pageSpread === CbxPageSpread.ODD || this.pageSpread === PdfPageSpread.ODD;
    for (let i = this.currentPage; i >= 0; i--) {
      if ((this.pages[i] % 2 === 1) === desiredOdd) {
        this.currentPage = i;
        this.updateProgress();
        return;
      }
    }
    for (let i = 0; i < this.pages.length; i++) {
      if ((this.pages[i] % 2 === 1) === desiredOdd) {
        this.currentPage = i;
        this.updateProgress();
        return;
      }
    }
  }

  // Quick settings handlers
  onFitModeChange(mode: CbxFitMode): void {
    this.fitMode = mode;
    this.quickSettingsService.setFitMode(mode);
    this.updateViewerSetting();
  }

  onScrollModeChange(mode: CbxScrollMode): void {
    this.scrollMode = mode;
    this.quickSettingsService.setScrollMode(mode);
    this.updateViewerSetting();

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.initializeInfiniteScroll();
      setTimeout(() => this.scrollToPage(this.currentPage), 100);
    }
  }

  onPageViewModeChange(mode: CbxPageViewMode): void {
    if (mode === CbxPageViewMode.TWO_PAGE && this.isPhonePortrait()) return;
    this.pageViewMode = mode;
    this.quickSettingsService.setPageViewMode(mode);
    this.alignCurrentPageToParity();
    this.footerService.setTwoPageView(this.isTwoPageView);
    this.updateViewerSetting();
  }

  onPageSpreadChange(spread: CbxPageSpread): void {
    this.pageSpread = spread;
    this.quickSettingsService.setPageSpread(spread);
    this.alignCurrentPageToParity();
    this.updateViewerSetting();
  }

  onBackgroundColorChange(color: CbxBackgroundColor): void {
    this.backgroundColor = color;
    this.quickSettingsService.setBackgroundColor(color);
    this.updateViewerSetting();
  }

  private initializeInfiniteScroll(): void {
    this.infiniteScrollPages = [];
    const endIndex = Math.min(this.currentPage + this.preloadCount, this.pages.length);
    for (let i = this.currentPage; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onScroll(event: Event): void {
    if (this.scrollMode !== CbxScrollMode.INFINITE || this.isLoadingMore) return;

    const container = event.target as HTMLElement;
    const scrollPosition = container.scrollTop + container.clientHeight;
    const scrollHeight = container.scrollHeight;

    if (scrollPosition >= scrollHeight * 0.8) {
      this.loadMorePages();
    }

    this.updateCurrentPageFromScroll(container);
  }

  private loadMorePages(): void {
    if (this.isLoadingMore) return;

    const lastLoadedIndex = this.infiniteScrollPages[this.infiniteScrollPages.length - 1];
    if (lastLoadedIndex >= this.pages.length - 1) return;

    this.isLoadingMore = true;
    const endIndex = Math.min(lastLoadedIndex + this.preloadCount + 1, this.pages.length);

    setTimeout(() => {
      for (let i = lastLoadedIndex + 1; i < endIndex; i++) {
        this.infiniteScrollPages.push(i);
      }
      this.isLoadingMore = false;
    }, 100);
  }

  private updateCurrentPageFromScroll(container: HTMLElement): void {
    const images = container.querySelectorAll('.page-image');
    const containerRect = container.getBoundingClientRect();

    for (let i = 0; i < images.length; i++) {
      const img = images[i] as HTMLElement;
      const rect = img.getBoundingClientRect();

      if (rect.top <= containerRect.top + containerRect.height / 2 &&
        rect.bottom >= containerRect.top + containerRect.height / 2) {
        const newPage = this.infiniteScrollPages[i];
        if (newPage !== this.currentPage) {
          this.currentPage = newPage;
          this.updateProgress();
          this.updateSessionProgress();
          this.updateFooterPage();
        }
        break;
      }
    }
  }

  private getPageImageUrl(pageIndex: number): string {
    return this.bookType === CbxReaderComponent.TYPE_PDF
      ? this.pdfReaderService.getPageImageUrl(this.bookId, this.pages[pageIndex])
      : this.cbxReaderService.getPageImageUrl(this.bookId, this.pages[pageIndex]);
  }

  get imageUrls(): string[] {
    if (!this.pages.length) return [];

    const urls: string[] = [];

    urls.push(this.getPageImageUrl(this.currentPage));

    if (this.isTwoPageView && this.currentPage + 1 < this.pages.length) {
      urls.push(this.getPageImageUrl(this.currentPage + 1));
    }

    return urls;
  }

  get infiniteScrollImageUrls(): string[] {
    return this.infiniteScrollPages.map(pageIndex => this.getPageImageUrl(pageIndex));
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = this.bookType === CbxReaderComponent.TYPE_CBX
      ? {
        cbxSettings: {
          pageSpread: this.pageSpread as CbxPageSpread,
          pageViewMode: this.pageViewMode as CbxPageViewMode,
          fitMode: this.fitMode as CbxFitMode,
          scrollMode: this.scrollMode as CbxScrollMode,
          backgroundColor: this.backgroundColor as CbxBackgroundColor,
        }
      }
      : {
        newPdfSettings: {
          pageSpread: this.pageSpread as PdfPageSpread,
          pageViewMode: this.pageViewMode as PdfPageViewMode,
        }
      };
    this.bookService.updateViewerSetting(bookSetting, this.bookId).subscribe();
  }

  updateProgress(): void {
    const percentage = this.pages.length > 0
      ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10
      : 0;

    if (this.bookType === CbxReaderComponent.TYPE_CBX) {
      this.bookService.saveCbxProgress(this.bookId, this.currentPage + 1, percentage).subscribe();
    }
    if (this.bookType === CbxReaderComponent.TYPE_PDF) {
      this.bookService.savePdfProgress(this.bookId, this.currentPage + 1, percentage).subscribe();
    }
  }

  private updateSessionProgress(): void {
    const percentage = this.pages.length > 0
      ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10
      : 0;
    this.readingSessionService.updateProgress(
      (this.currentPage + 1).toString(),
      percentage
    );
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.pages.length) return;

    const targetIndex = page - 1;
    if (targetIndex === this.currentPage) return;

    this.currentPage = targetIndex;

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.ensurePageLoaded(targetIndex);
      this.scrollToPage(targetIndex);
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    } else {
      this.alignCurrentPageToParity();
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    }
  }

  firstPage(): void {
    this.goToPage(1);
  }

  lastPage(): void {
    this.goToPage(this.pages.length);
  }

  private scrollToPage(pageIndex: number): void {
    this.ensurePageLoaded(pageIndex);

    setTimeout(() => {
      const container = document.querySelector('.image-container.infinite-scroll') as HTMLElement;
      if (!container) return;

      const images = container.querySelectorAll('.page-image');
      const indexInScroll = this.infiniteScrollPages.indexOf(pageIndex);

      if (indexInScroll >= 0 && indexInScroll < images.length) {
        const targetImage = images[indexInScroll] as HTMLElement;
        targetImage.scrollIntoView({behavior: 'smooth', block: 'start'});
      }
    }, 100);
  }

  private ensurePageLoaded(pageIndex: number): void {
    if (this.infiniteScrollPages.includes(pageIndex)) return;

    this.infiniteScrollPages = [];
    const startIndex = Math.max(0, pageIndex - 1);
    const endIndex = Math.min(pageIndex + this.preloadCount, this.pages.length);

    for (let i = startIndex; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onImageClick(): void {
    this.visibilityManager.togglePinned();
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent) {
    if (event.key === 'ArrowRight') this.nextPage();
    else if (event.key === 'ArrowLeft') this.previousPage();
  }

  @HostListener('touchstart', ['$event'])
  onTouchStart(event: TouchEvent) {
    this.touchStartX = event.changedTouches[0].screenX;
  }

  @HostListener('touchend', ['$event'])
  onTouchEnd(event: TouchEvent) {
    this.touchEndX = event.changedTouches[0].screenX;
    this.handleSwipeGesture();
  }

  @HostListener('window:resize')
  onResize() {
    this.visibilityManager.updateWindowHeight(window.innerHeight);
    this.enforcePortraitSinglePageView();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.visibilityManager.handleMouseMove(event.clientY);
  }

  @HostListener('document:mouseleave', ['$event'])
  onMouseLeave(event: MouseEvent): void {
    this.visibilityManager.handleMouseLeave();
  }

  private handleSwipeGesture() {
    const delta = this.touchEndX - this.touchStartX;
    if (Math.abs(delta) >= 50) delta < 0 ? this.nextPage() : this.previousPage();
  }

  private enforcePortraitSinglePageView() {
    if (this.isPhonePortrait() && this.isTwoPageView) {
      this.pageViewMode = this.bookType === CbxReaderComponent.TYPE_CBX ? CbxPageViewMode.SINGLE_PAGE : PdfPageViewMode.SINGLE_PAGE;
      this.quickSettingsService.setPageViewMode(this.pageViewMode);
      this.footerService.setTwoPageView(false);
      this.updateViewerSetting();
    }
  }

  private isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  get isAtLastPage(): boolean {
    return this.currentPage >= this.pages.length - 1;
  }

  navigateToPreviousBook(): void {
    if (this.previousBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.previousBookInSeries.id]);
    }
  }

  navigateToNextBook(): void {
    if (this.nextBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.nextBookInSeries.id]);
    }
  }

  private loadSeriesNavigationAsync(book: Book): void {
    this.bookService.bookState$.pipe(
      filter((state: BookState) => state.loaded),
      first(),
      timeout(10000)
    ).subscribe({
      next: () => {
        this.loadSeriesNavigation(book);
      },
      error: (err) => {
        console.warn('[SeriesNav] BookService state loading timed out or failed, series navigation will be disabled:', err);
      }
    });
  }

  private loadSeriesNavigation(book: Book): void {
    this.bookService.getBooksInSeries(book.id).subscribe({
      next: (seriesBooks) => {
        const sortedBySeriesNumber = this.sortBooksBySeriesNumber(seriesBooks);
        const currentBookIndex = sortedBySeriesNumber.findIndex(b => b.id === book.id);

        if (currentBookIndex === -1) {
          console.warn('[SeriesNav] Current book not found in series');
          return;
        }

        const hasPreviousBook = currentBookIndex > 0;
        const hasNextBook = currentBookIndex < sortedBySeriesNumber.length - 1;

        this.previousBookInSeries = hasPreviousBook ? sortedBySeriesNumber[currentBookIndex - 1] : null;
        this.nextBookInSeries = hasNextBook ? sortedBySeriesNumber[currentBookIndex + 1] : null;

        // Update footer service with series info
        this.footerService.setSeriesBooks(this.previousBookInSeries, this.nextBookInSeries);
        this.footerService.setHasSeries(true);
      },
      error: (err) => {
        console.error('[SeriesNav] Failed to load series information:', err);
      }
    });
  }

  private sortBooksBySeriesNumber(books: Book[]): Book[] {
    return books.sort((bookA, bookB) => {
      const seriesNumberA = bookA.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      const seriesNumberB = bookB.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      return seriesNumberA - seriesNumberB;
    });
  }

  getBookDisplayTitle(book: Book | null): string {
    if (!book) return '';

    const parts: string[] = [];

    if (book.metadata?.seriesNumber) {
      parts.push(`#${book.metadata.seriesNumber}`);
    }

    const title = book.metadata?.title || book.fileName;
    if (title) {
      parts.push(title);
    }

    if (book.metadata?.subtitle) {
      parts.push(book.metadata.subtitle);
    }

    return parts.join(' - ');
  }

  ngOnDestroy(): void {
    this.endReadingSession();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private endReadingSession(): void {
    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.pages.length > 0 ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10 : 0;
      this.readingSessionService.endSession((this.currentPage + 1).toString(), percentage);
    }
  }

  // Bookmark methods
  private updateBookmarkState(): void {
    this.isCurrentPageBookmarked = this.sidebarService.isPageBookmarked(this.currentPage + 1);
  }

  toggleBookmark(): void {
    this.sidebarService.toggleBookmark(this.currentPage + 1);
  }

  // Notes state methods
  private updateNotesState(): void {
    this.currentPageHasNotes = this.sidebarService.pageHasNotes(this.currentPage + 1);
  }

  // Note dialog methods
  openNoteDialog(): void {
    this.editingNote = null;
    this.noteDialogData = {
      pageNumber: this.currentPage + 1
    };
    this.showNoteDialog = true;
  }

  private openNoteDialogForEdit(note: BookNoteV2): void {
    this.editingNote = note;
    this.noteDialogData = {
      pageNumber: parseInt(note.cfi, 10) || this.currentPage + 1,
      noteId: note.id,
      noteContent: note.noteContent,
      color: note.color
    };
    this.showNoteDialog = true;
  }

  onNoteSave(result: CbxNoteDialogResult): void {
    if (this.editingNote) {
      this.sidebarService.updateNote(this.editingNote.id, result.noteContent, result.color);
    } else if (this.noteDialogData) {
      this.sidebarService.createNote(this.noteDialogData.pageNumber, result.noteContent, result.color);
    }
    this.showNoteDialog = false;
    this.noteDialogData = null;
    this.editingNote = null;
  }

  onNoteCancel(): void {
    this.showNoteDialog = false;
    this.noteDialogData = null;
    this.editingNote = null;
  }
}
