import {Component, CUSTOM_ELEMENTS_SCHEMA, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule, Location} from '@angular/common';
import {forkJoin, Observable, of, Subject, throwError} from 'rxjs';
import {catchError, switchMap, takeUntil, tap} from 'rxjs/operators';
import {ReaderLoaderService} from './services/reader-loader.service';
import {ReaderViewManagerService} from './services/reader-view-manager.service';
import {ReaderStateService} from './services/reader-state.service';
import {ReaderStyleService} from './services/reader-style.service';
import {ReaderBookmarkService} from './services/reader-bookmark.service';
import {BookService} from '../../book/service/book.service';
import {ActivatedRoute} from '@angular/router';
import {BookMark, BookMarkService} from '../../../shared/service/book-mark.service';
import {BookPatchService} from '../../book/service/book-patch.service';
import {EpubCustomFontService} from '../epub-reader/service/epub-custom-font.service';
import {Book, EbookViewerSetting} from '../../book/model/book.model';
import {ReaderHeaderComponent} from './reader-layout/header/reader-header.component';
import {ReaderSidebarComponent} from './reader-layout/sidebar/reader-sidebar.component';
import {ReaderNavbarComponent} from './reader-layout/navbar/reader-navbar.component';
import {ReaderSettingsDialogComponent} from './reader-layout/header/reader-settings-dialog.component';
import {ReaderBookMetadataDialogComponent} from './reader-layout/sidebar/reader-book-metadata-dialog.component';
import {ReadingSessionService} from '../../../shared/service/reading-session.service';
import {TocItem} from 'epubjs';
import {PageInfo, ThemeInfo} from './utils/reader-header-footer.util';
import {ReaderHeaderFooterVisibilityManager} from './utils/reader-header-footer-visibility.util';

@Component({
  selector: 'app-ebook-reader',
  standalone: true,
  imports: [
    CommonModule,
    ReaderHeaderComponent,
    ReaderSettingsDialogComponent,
    ReaderBookMetadataDialogComponent,
    ReaderSidebarComponent,
    ReaderNavbarComponent
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  providers: [
    ReaderLoaderService,
    ReaderViewManagerService,
    ReaderStateService,
    ReaderStyleService,
    ReaderBookmarkService
  ],
  templateUrl: './ebook-reader.component.html',
  styleUrls: ['./ebook-reader.component.scss']
})
export class EbookReaderComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private loaderService = inject(ReaderLoaderService);
  private styleService = inject(ReaderStyleService);
  private bookService = inject(BookService);
  private route = inject(ActivatedRoute);
  private bookMarkService = inject(BookMarkService);
  private bookPatchService = inject(BookPatchService);
  private epubCustomFontService = inject(EpubCustomFontService);
  private bookmarkService = inject(ReaderBookmarkService);
  private readingSessionService = inject(ReadingSessionService);

  public viewManager = inject(ReaderViewManagerService);
  public stateService = inject(ReaderStateService);
  protected location = inject(Location);
  protected bookId!: number;

  private hasLoadedOnce = false;
  private hasStartedSession = false;
  private _fileUrl: string | null = null;
  private currentCfi: string | null = null;

  private visibilityManager!: ReaderHeaderFooterVisibilityManager;

  isLoading = true;
  showControls = false;
  showChapters = false;
  showMetadata = false;
  isCurrentCfiBookmarked = false;
  forceHeaderVisible = false;
  forceNavbarVisible = false;

  chapters: TocItem[] = [];
  bookmarks: BookMark[] = [];
  book: Book | null = null;
  coverUpdatedOn: string | undefined;
  currentChapterName: string | null = null;
  currentChapterHref: string | null = null;
  currentProgressData: any = null;
  private currentPageInfo: PageInfo | undefined;
  private relocateTimeout: any;
  private sectionFractionsTimeout: any;

  sectionFractions: number[] = [];

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.forceHeaderVisible = state.headerVisible;
      this.forceNavbarVisible = state.footerVisible;
    });

    this.isLoading = true;
    this.initializeFoliate().pipe(
      switchMap(() => this.epubCustomFontService.loadAndCacheFonts()),
      tap(() => this.stateService.refreshCustomFonts()),
      switchMap(() => this.setupView()),
      switchMap(() => this.loadBookFromAPI()),
      tap(() => {
        this.loadBookmarks();
        this.subscribeToStateChanges();
        this.subscribeToViewEvents();
        this.isLoading = false;
      }),
      catchError(err => {
        this.isLoading = false;
        return of(null);
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.viewManager.destroy();
    this.bookmarkService.reset();
    this.epubCustomFontService.cleanup();

    if (this.readingSessionService.isSessionActive()) {
      const progress = typeof this.currentProgressData?.fraction === 'number'
        ? Math.round(this.currentProgressData.fraction * 100 * 100) / 100
        : undefined;
      this.readingSessionService.endSession(this.currentCfi || undefined, progress);
    }

    if (this._fileUrl) {
      URL.revokeObjectURL(this._fileUrl);
      this._fileUrl = null;
    }
  }

  private initializeFoliate(): Observable<void> {
    return this.loaderService.loadFoliateScript().pipe(
      switchMap(() => this.loaderService.waitForCustomElement())
    );
  }

  private setupView(): Observable<void> {
    const container = document.getElementById('foliate-container');
    if (!container) {
      return throwError(() => new Error('Container not found'));
    }
    container.setAttribute('tabindex', '0');
    this.viewManager.createView(container);
    return of(undefined);
  }

  private loadBookFromAPI(): Observable<void> {
    this.bookId = +this.route.snapshot.paramMap.get('bookId')!;

    return this.stateService.initializeState(this.bookId).pipe(
      switchMap(() => forkJoin({
        state: this.stateService.initializeState(this.bookId),
        book: this.bookService.getBookByIdFromAPI(this.bookId, false),
        fileBlob: this.bookService.getFileContent(this.bookId)
      })),
      switchMap(({book, fileBlob}) => {
        this.book = book;
        this.coverUpdatedOn = book.metadata?.coverUpdatedOn;
        const fileUrl = URL.createObjectURL(fileBlob);
        this._fileUrl = fileUrl;

        return this.viewManager.loadEpub(fileUrl).pipe(
          tap(() => {
            this.applyStyles();
            this.chapters = this.viewManager.getChapters();
          }),
          switchMap(() => this.viewManager.getMetadata()),
          switchMap(() => {
            if (!this.hasLoadedOnce) {
              this.hasLoadedOnce = true;
              return this.viewManager.goTo(book.epubProgress!.cfi);
            }
            return of(undefined);
          })
        );
      })
    );
  }

  private loadBookmarks(): void {
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => {
        this.bookmarks = bookmarks;
        this.updateIsCurrentCfiBookmarked();
      });
  }

  private subscribeToStateChanges(): void {
    this.stateService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.applyStyles());
  }

  private subscribeToViewEvents(): void {
    this.viewManager.events$
      .pipe(takeUntil(this.destroy$))
      .subscribe(event => {
        switch (event.type) {
          case 'load':
            this.applyStyles();
            this.chapters = this.viewManager.getChapters();
            this.updateSectionFractions();
            break;
          case 'relocate':
            if (this.relocateTimeout) clearTimeout(this.relocateTimeout);
            this.relocateTimeout = setTimeout(() => {
              this.handleRelocateEvent(event.detail);
            }, 100);

            if (this.sectionFractionsTimeout) clearTimeout(this.sectionFractionsTimeout);
            this.sectionFractionsTimeout = setTimeout(() => {
              this.updateSectionFractions();
            }, 500);
            break;
          case 'middle-single-tap':
            this.toggleHeaderNavbarPinned();
            break;
        }
      });
  }

  private updateSectionFractions(): void {
    this.sectionFractions = this.viewManager.getSectionFractions();
  }

  private handleRelocateEvent(detail: any): void {
    this.currentProgressData = detail;

    const cfi = detail?.cfi ?? null;
    const href = detail?.pageItem?.href ?? detail?.tocItem?.href ?? null;
    const percentage = typeof detail?.fraction === 'number' ? detail.fraction * 100 : null;

    if (!this.hasStartedSession && cfi && percentage !== null) {
      this.hasStartedSession = true;
      this.readingSessionService.startSession(this.book!.id, this.book?.bookType!, cfi, percentage);
    }

    if (cfi && percentage !== null) {
      this.bookPatchService.saveEpubProgress(this.bookId, cfi, href, percentage);
      this.readingSessionService.updateProgress(cfi, percentage);
    }

    const chapterLabel = detail?.tocItem?.label;
    if (chapterLabel && chapterLabel !== this.currentChapterName) {
      this.currentChapterName = chapterLabel;
    }

    if (href && href !== this.currentChapterHref) {
      this.currentChapterHref = href;
    }

    if (detail?.section) {
      const percentCompleted = Math.round((detail.fraction * 100) * 10) / 10;
      const totalMinutes = detail.time?.section ?? 0;

      const hours = Math.floor(totalMinutes / 60);
      const minutes = Math.floor(totalMinutes % 60);
      const seconds = Math.round((totalMinutes - Math.floor(totalMinutes)) * 60);

      const parts: string[] = [];
      if (hours) parts.push(`${hours}h`);
      if (minutes) parts.push(`${minutes}m`);
      if (seconds || parts.length === 0) parts.push(`${seconds}s`);

      const sectionTimeText = parts.join(' ');

      this.currentPageInfo = {
        percentCompleted,
        sectionTimeText
      };
    }

    if (this.stateService.currentState.flow === 'paginated') {
      const renderer = this.viewManager.getRenderer();
      const theme: ThemeInfo = {
        fg: this.stateService.currentState.theme.fg || this.stateService.currentState.theme.light.fg,
        bg: this.stateService.currentState.theme.bg || this.stateService.currentState.theme.light.bg
      };

      if (renderer && renderer.heads && renderer.feet) {
        this.viewManager.updateHeadersAndFooters(this.currentChapterName || '', this.currentPageInfo, theme);
      } else {
        this.viewManager.updateHeadersAndFooters(this.currentChapterName || '', this.currentPageInfo, theme);
      }
    }

    if (cfi) {
      this.currentCfi = cfi;
      this.updateIsCurrentCfiBookmarked();
      this.bookmarkService.updateCurrentPosition(cfi, chapterLabel);
    }
  }

  private updateIsCurrentCfiBookmarked(): void {
    if (!this.currentCfi || !this.bookmarks?.length) {
      this.isCurrentCfiBookmarked = false;
      return;
    }
    this.isCurrentCfiBookmarked = this.bookmarks.some(b => b.cfi === this.currentCfi);
  }

  private applyStyles(): void {
    const renderer = this.viewManager.getRenderer();
    if (renderer) {
      this.styleService.applyStylesToRenderer(renderer, this.stateService.currentState);
      if (this.stateService.currentState.flow) {
        renderer.setAttribute?.('flow', this.stateService.currentState.flow);
      }
    }
  }

  private syncSettingsToBackend(): void {
    const setting: EbookViewerSetting = {
      lineHeight: this.stateService.currentState.lineHeight,
      justify: this.stateService.currentState.justify,
      hyphenate: this.stateService.currentState.hyphenate,
      maxColumnCount: this.stateService.currentState.maxColumnCount,
      gap: this.stateService.currentState.gap,
      fontSize: this.stateService.currentState.fontSize,
      theme: typeof this.stateService.currentState.theme === 'object' && 'name' in this.stateService.currentState.theme
        ? this.stateService.currentState.theme.name
        : (this.stateService.currentState.theme as any),
      maxInlineSize: this.stateService.currentState.maxInlineSize,
      maxBlockSize: this.stateService.currentState.maxBlockSize,
      fontFamily: this.stateService.currentState.fontFamily,
      isDark: this.stateService.currentState.isDark,
      flow: this.stateService.currentState.flow,
    };
    this.bookService.updateViewerSetting({ebookSettings: setting}, this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe();
  }

  onChapterClick(href: string): void {
    this.viewManager.goTo(href).pipe(
      tap(() => this.showChapters = false),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  onBookmarkClick(cfi: string): void {
    this.viewManager.goTo(cfi).pipe(
      tap(() => {
        this.showChapters = false;
        this.currentCfi = cfi;
        this.updateIsCurrentCfiBookmarked();
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  onCreateBookmark(): void {
    this.bookmarkService.createBookmarkAtCurrentPosition(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(success => {
        if (success) {
          this.loadBookmarks();
        }
      });
  }

  onDeleteBookmark(bookmarkId: number): void {
    this.bookMarkService.deleteBookmark(bookmarkId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.loadBookmarks(),
        error: () => {
        }
      });
  }

  onProgressChange(fraction: number): void {
    this.viewManager.goToFraction(fraction)
      .pipe(takeUntil(this.destroy$))
      .subscribe();
  }

  onToggleDarkMode(): void {
    this.stateService.toggleDarkMode();
    this.syncSettingsToBackend();
  }

  onIncreaseFontSize(): void {
    this.stateService.updateFontSize(1);
    this.syncSettingsToBackend();
  }

  onDecreaseFontSize(): void {
    this.stateService.updateFontSize(-1);
    this.syncSettingsToBackend();
  }

  onIncreaseLineHeight(): void {
    this.stateService.updateLineHeight(0.1);
    this.syncSettingsToBackend();
  }

  onDecreaseLineHeight(): void {
    this.stateService.updateLineHeight(-0.1);
    this.syncSettingsToBackend();
  }

  onSetFlow(flow: 'paginated' | 'scrolled'): void {
    this.stateService.setFlow(flow);
    this.syncSettingsToBackend();

    if (flow === 'paginated' && this.currentChapterName) {
      setTimeout(() => {
        const renderer = this.viewManager.getRenderer();
        if (renderer && renderer.heads && renderer.feet) {
          const theme: ThemeInfo = {
            fg: this.stateService.currentState.theme.fg || this.stateService.currentState.theme.light.fg,
            bg: this.stateService.currentState.theme.bg || this.stateService.currentState.theme.light.bg
          };
          this.viewManager.updateHeadersAndFooters(this.currentChapterName || '', this.currentPageInfo, theme);
        }
      }, 50);
    }
  }

  private toggleHeaderNavbarPinned(): void {
    this.visibilityManager.togglePinned();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.visibilityManager.handleMouseMove(event.clientY);
  }

  @HostListener('document:mouseleave', ['$event'])
  onMouseLeave(event: MouseEvent): void {
    this.visibilityManager.handleMouseLeave();
  }

  @HostListener('window:resize', ['$event'])
  onWindowResize(event: Event): void {
    this.visibilityManager.updateWindowHeight(window.innerHeight);
  }
}
