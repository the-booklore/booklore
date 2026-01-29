import {Component, CUSTOM_ELEMENTS_SCHEMA, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {forkJoin, Observable, of, Subject, throwError} from 'rxjs';
import {catchError, switchMap, takeUntil, tap} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {ReaderLoaderService} from './core/loader.service';
import {ReaderViewManagerService, TocItem} from './core/view-manager.service';
import {ReaderStateService} from './state/reader-state.service';
import {ReaderStyleService} from './core/style.service';
import {ReaderBookmarkService} from './features/bookmarks/bookmark.service';
import {ReaderAnnotationHttpService} from './features/annotations/annotation.service';
import {ReaderProgressService} from './state/progress.service';
import {ReaderSelectionService} from './features/selection/selection.service';
import {ReaderSidebarService} from './layout/sidebar/sidebar.service';
import {ReaderLeftSidebarService} from './layout/panel/panel.service';
import {ReaderHeaderService} from './layout/header/header.service';
import {ReaderNoteService} from './features/notes/note.service';
import {BookService} from '../../book/service/book.service';
import {ActivatedRoute} from '@angular/router';
import {Book} from '../../book/model/book.model';
import {ReaderHeaderComponent} from './layout/header/header.component';
import {ReaderSidebarComponent} from './layout/sidebar/sidebar.component';
import {ReaderLeftSidebarComponent} from './layout/panel/panel.component';
import {ReaderNavbarComponent} from './layout/footer/footer.component';
import {ReaderSettingsDialogComponent} from './dialogs/settings-dialog.component';
import {ReaderQuickSettingsComponent} from './layout/header/quick-settings.component';
import {ReaderBookMetadataDialogComponent} from './dialogs/metadata-dialog.component';
import {ReaderHeaderFooterVisibilityManager} from './shared/visibility.util';
import {EpubCustomFontService} from './features/fonts/custom-font.service';
import {TextSelectionPopupComponent, TextSelectionAction} from './shared/selection-popup.component';
import {ReaderNoteDialogComponent, NoteDialogData, NoteDialogResult} from './dialogs/note-dialog.component';
import {RsvpService} from './features/rsvp/rsvp.service';
import {RsvpOverlayComponent} from './features/rsvp/rsvp-overlay.component';

@Component({
  selector: 'app-ebook-reader',
  standalone: true,
  imports: [
    CommonModule,
    ReaderHeaderComponent,
    ReaderSettingsDialogComponent,
    ReaderQuickSettingsComponent,
    ReaderBookMetadataDialogComponent,
    ReaderSidebarComponent,
    ReaderLeftSidebarComponent,
    ReaderNavbarComponent,
    TextSelectionPopupComponent,
    ReaderNoteDialogComponent,
    RsvpOverlayComponent
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  providers: [
    MessageService,
    ReaderLoaderService,
    ReaderViewManagerService,
    ReaderStateService,
    ReaderStyleService,
    ReaderBookmarkService,
    ReaderAnnotationHttpService,
    ReaderProgressService,
    ReaderSelectionService,
    ReaderSidebarService,
    ReaderLeftSidebarService,
    ReaderHeaderService,
    ReaderNoteService,
    RsvpService
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
  private epubCustomFontService = inject(EpubCustomFontService);
  private annotationService = inject(ReaderAnnotationHttpService);
  public progressService = inject(ReaderProgressService);
  private selectionService = inject(ReaderSelectionService);
  private headerService = inject(ReaderHeaderService);
  private noteService = inject(ReaderNoteService);
  private rsvpService = inject(RsvpService);

  public sidebarService = inject(ReaderSidebarService);
  public leftSidebarService = inject(ReaderLeftSidebarService);
  public viewManager = inject(ReaderViewManagerService);
  public stateService = inject(ReaderStateService);

  protected bookId!: number;

  private hasLoadedOnce = false;
  private _fileUrl: string | null = null;
  private visibilityManager!: ReaderHeaderFooterVisibilityManager;
  private relocateTimeout: any;
  private sectionFractionsTimeout: any;

  isLoading = true;
  showQuickSettings = false;
  showControls = false;
  showMetadata = false;
  isCurrentCfiBookmarked = false;
  forceNavbarVisible = false;
  headerVisible = false;
  book: Book | null = null;
  sectionFractions: number[] = [];

  showSelectionPopup = false;
  popupPosition = { x: 0, y: 0 };
  showPopupBelow = false;
  overlappingAnnotationId: number | null = null;
  selectedText = '';

  showNoteDialog = false;
  noteDialogData: NoteDialogData | null = null;
  showRsvp = false;
  showRsvpStartChoice = false;
  rsvpStartOptions: {
    hasSavedPosition: boolean;
    hasSelection: boolean;
    selectionText?: string;
    firstVisibleWordIndex: number;
  } = { hasSavedPosition: false, hasSelection: false, firstVisibleWordIndex: 0 };
  private rsvpHighlightCfi: string | null = null;
  rsvpChapters: TocItem[] = [];

  get currentProgressData(): any {
    return this.progressService.currentProgressData;
  }

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerVisible = state.headerVisible;
      this.headerService.setForceVisible(state.headerVisible);
      this.forceNavbarVisible = state.footerVisible;
    });

    this.selectionService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        this.showSelectionPopup = state.visible;
        this.popupPosition = state.position;
        this.showPopupBelow = state.showBelow;
        this.overlappingAnnotationId = state.overlappingAnnotationId;
        this.selectedText = state.selectedText;
      });

    this.sidebarService.showMetadata$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showMetadata = true);

    this.noteService.dialogState$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        this.showNoteDialog = state.visible;
        this.noteDialogData = state.data;
      });

    this.headerService.showControls$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showQuickSettings = true);

    this.headerService.showMetadata$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showMetadata = true);

    this.headerService.startRsvp$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        // Load chapters for the RSVP chapter selector
        this.rsvpChapters = this.viewManager.getChapters();
        // Get the current CFI directly from the view (not cached) to avoid race conditions
        // after page navigation. The progressService.currentCfi may be stale due to the
        // 100ms timeout in the relocate handler.
        const currentCfi = this.viewManager.getCurrentCfi() || this.progressService.currentCfi;
        this.rsvpService.setCurrentCfi(currentCfi);
        // Get any selected text
        const selection = this.viewManager.getSelection();
        const selectionText = selection?.text || '';
        // Request start - this will check for saved position, selection, and visible word
        this.rsvpService.requestStart(selectionText);
      });

    this.rsvpService.showStartChoice$
      .pipe(takeUntil(this.destroy$))
      .subscribe((options) => {
        this.rsvpStartOptions = options;
        // Always show the start dialog to give users control
        this.showRsvpStartChoice = true;
      });

    this.rsvpService.stopPosition$
      .pipe(takeUntil(this.destroy$))
      .subscribe(positionInfo => {
        console.log('[RSVP] stopPosition$ received:', positionInfo);
        if (positionInfo && positionInfo.totalWords > 0) {
          console.log('[RSVP] Position info valid, checking range and docIndex');
          // Create highlight for the word (don't navigate - stay on current page)
          if (positionInfo.range && positionInfo.docIndex !== undefined) {
            console.log('[RSVP] Calling highlightRsvpWord');
            this.highlightRsvpWord(positionInfo.range, positionInfo.docIndex);
          } else {
            console.log('[RSVP] Missing range or docIndex', {range: positionInfo.range, docIndex: positionInfo.docIndex});
          }
        }
      });

    this.isLoading = true;
    this.initializeFoliate().pipe(
      switchMap(() => this.epubCustomFontService.loadAndCacheFonts()),
      tap(() => this.stateService.refreshCustomFonts()),
      switchMap(() => this.setupView()),
      tap(() => {
        this.subscribeToViewEvents();
        this.subscribeToStateChanges();
      }),
      switchMap(() => this.loadBookFromAPI()),
      tap(() => this.isLoading = false),
      catchError(() => {
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
    this.annotationService.reset();
    this.progressService.endSession();
    this.progressService.reset();
    this.selectionService.reset();
    this.sidebarService.reset();
    this.leftSidebarService.reset();
    this.headerService.reset();
    this.noteService.reset();
    this.rsvpService.reset();
    this.epubCustomFontService.cleanup();

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
        book: this.bookService.getBookByIdFromAPI(this.bookId, false)
      })),
      switchMap(({book}) => {
        this.book = book;

        this.progressService.initialize(this.bookId, book.bookType!);
        this.selectionService.initialize(this.bookId, this.destroy$);
        this.headerService.initialize(this.bookId, book.metadata?.title || '', this.destroy$);
        this.rsvpService.initialize(this.bookId);

        // Use streaming for EPUB if query param is set, blob loading otherwise (default)
        const useStreaming = this.route.snapshot.queryParamMap.get('streaming') === 'true';
        const loadBook$ = book.bookType === 'EPUB' && useStreaming
          ? this.viewManager.loadEpubStreaming(this.bookId)
          : this.loadBookBlob();

        return loadBook$.pipe(
          tap(() => {
            this.applyStyles();
            this.sidebarService.initialize(this.bookId, book, this.destroy$);
            this.leftSidebarService.initialize(this.bookId, this.destroy$);
            this.noteService.initialize(this.bookId, this.destroy$);
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

  private loadBookBlob(): Observable<void> {
    return this.bookService.getFileContent(this.bookId).pipe(
      switchMap(fileBlob => {
        const fileUrl = URL.createObjectURL(fileBlob);
        this._fileUrl = fileUrl;
        return this.viewManager.loadEpub(fileUrl);
      })
    );
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
            this.sidebarService.updateChapters();
            this.updateSectionFractions();
            break;
          case 'relocate':
            if (this.relocateTimeout) clearTimeout(this.relocateTimeout);
            this.relocateTimeout = setTimeout(() => {
              this.progressService.handleRelocateEvent(event.detail);
              this.updateBookmarkIndicator();
            }, 100);

            if (this.sectionFractionsTimeout) clearTimeout(this.sectionFractionsTimeout);
            this.sectionFractionsTimeout = setTimeout(() => {
              this.updateSectionFractions();
            }, 500);
            break;
          case 'middle-single-tap':
            this.toggleHeaderNavbarPinned();
            break;
          case 'text-selected':
            this.selectionService.handleTextSelected(event.detail, event.popupPosition);
            break;
        }
      });
  }

  private updateSectionFractions(): void {
    this.sectionFractions = this.viewManager.getSectionFractions();
  }

  private updateBookmarkIndicator(): void {
    const currentCfi = this.progressService.currentCfi;
    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => {
        this.isCurrentCfiBookmarked = currentCfi
          ? bookmarks.some(b => b.cfi === currentCfi)
          : false;
        this.headerService.setCurrentCfiBookmarked(this.isCurrentCfiBookmarked);
      });
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

  onProgressChange(fraction: number): void {
    this.viewManager.goToFraction(fraction)
      .pipe(takeUntil(this.destroy$))
      .subscribe();
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

  onHeaderTriggerZoneEnter(): void {
    this.visibilityManager.handleHeaderZoneEnter();
  }

  onFooterTriggerZoneEnter(): void {
    this.visibilityManager.handleFooterZoneEnter();
  }

  handleSelectionAction(action: TextSelectionAction): void {
    if (action.type === 'note') {
      this.noteService.openNewNoteDialog();
    } else {
      this.selectionService.handleAction(action);
    }
  }

  onNoteSave(result: NoteDialogResult): void {
    this.noteService.saveNote(result);
  }

  onNoteCancel(): void {
    this.noteService.closeDialog();
  }

  onRsvpClose(): void {
    this.showRsvp = false;
  }

  onRsvpRequestNextPage(): void {
    this.viewManager.nextAsync()
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        // Wait for relocate event to be fully processed
        // The relocateTimeout in subscribeToViewEvents uses 100ms
        // We need to wait a bit longer for the renderer to update
        setTimeout(() => {
          // Update CFI for the new page
          const currentCfi = this.progressService.currentCfi;
          this.rsvpService.setCurrentCfi(currentCfi);
          // Additional delay to ensure content is rendered
          setTimeout(() => {
            this.rsvpService.loadNextPageContent();
          }, 150);
        }, 200);
      });
  }

  onRsvpChapterSelect(href: string): void {
    this.viewManager.goTo(href)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        // Wait for navigation and content to load
        setTimeout(() => {
          const currentCfi = this.viewManager.getCurrentCfi() || this.progressService.currentCfi;
          this.rsvpService.setCurrentCfi(currentCfi);
          // Reload RSVP content for the new chapter
          setTimeout(() => {
            this.rsvpService.loadNextPageContent();
          }, 150);
        }, 200);
      });
  }

  private highlightRsvpWord(range: Range, docIndex: number): void {
    try {
      console.log('[RSVP] highlightRsvpWord called', {range, docIndex});

      // Expand range to include the entire sentence
      const sentenceRange = this.expandRangeToSentence(range);
      const rangeToUse = sentenceRange || range;

      const cfi = (this.viewManager as any).view?.getCFI(docIndex, rangeToUse);
      console.log('[RSVP] Generated CFI:', cfi);
      if (!cfi) {
        console.log('[RSVP] No CFI generated, returning');
        return;
      }

      // Clear previous highlight
      this.clearRsvpHighlight();

      // Navigate to the word's location so it's visible
      console.log('[RSVP] Navigating to CFI...');
      this.viewManager.goTo(cfi).pipe(
        takeUntil(this.destroy$)
      ).subscribe(() => {
        console.log('[RSVP] Navigation complete, adding annotation');
        // Add underline annotation for the sentence
        this.rsvpHighlightCfi = cfi;
        this.viewManager.addAnnotation({
          value: cfi,
          color: '#ff0000',
          style: 'underline'
        }).pipe(takeUntil(this.destroy$)).subscribe(() => {
          console.log('[RSVP] Annotation added successfully');
        });
      });
    } catch (e) {
      console.error('[RSVP] Error in highlightRsvpWord:', e);
    }
  }

  private expandRangeToSentence(wordRange: Range): Range | null {
    try {
      const doc = wordRange.startContainer.ownerDocument;
      if (!doc) return null;

      // Get the text content around the word
      const container = wordRange.commonAncestorContainer;
      let textNode = container;

      // If the container is not a text node, find the text node
      if (textNode.nodeType !== Node.TEXT_NODE) {
        textNode = wordRange.startContainer;
      }

      if (textNode.nodeType !== Node.TEXT_NODE || !textNode.textContent) {
        return null;
      }

      const fullText = textNode.textContent;
      const wordStart = wordRange.startOffset;

      // Find sentence start (look for . ! ? or start of text)
      let sentenceStart = wordStart;
      for (let i = wordStart - 1; i >= 0; i--) {
        const char = fullText[i];
        if (char === '.' || char === '!' || char === '?') {
          sentenceStart = i + 1;
          // Skip whitespace after punctuation
          while (sentenceStart < fullText.length && /\s/.test(fullText[sentenceStart])) {
            sentenceStart++;
          }
          break;
        }
        if (i === 0) {
          sentenceStart = 0;
        }
      }

      // Find sentence end (look for . ! ? or end of text)
      let sentenceEnd = wordRange.endOffset;
      for (let i = wordRange.endOffset; i < fullText.length; i++) {
        const char = fullText[i];
        if (char === '.' || char === '!' || char === '?') {
          sentenceEnd = i + 1;
          break;
        }
        if (i === fullText.length - 1) {
          sentenceEnd = fullText.length;
        }
      }

      // Create new range for the sentence
      const sentenceRange = doc.createRange();
      sentenceRange.setStart(textNode, Math.max(0, sentenceStart));
      sentenceRange.setEnd(textNode, Math.min(fullText.length, sentenceEnd));

      return sentenceRange;
    } catch (e) {
      console.error('[RSVP] Error expanding range to sentence:', e);
      return null;
    }
  }

  private clearRsvpHighlight(): void {
    if (this.rsvpHighlightCfi) {
      this.viewManager.deleteAnnotation(this.rsvpHighlightCfi)
        .pipe(takeUntil(this.destroy$))
        .subscribe();
      this.rsvpHighlightCfi = null;
    }
  }

  onRsvpStartFromBeginning(): void {
    this.showRsvpStartChoice = false;
    this.clearRsvpHighlight();
    this.showRsvp = true;
    this.rsvpService.startFromBeginning();
  }

  onRsvpStartFromSaved(): void {
    this.showRsvpStartChoice = false;
    this.clearRsvpHighlight();
    this.showRsvp = true;
    this.rsvpService.startFromSavedPosition();
  }

  onRsvpStartChoiceCancel(): void {
    this.showRsvpStartChoice = false;
  }

  onRsvpStartFromCurrentPosition(): void {
    this.showRsvpStartChoice = false;
    this.clearRsvpHighlight();
    this.showRsvp = true;
    this.rsvpService.startFromCurrentPosition();
  }

  onRsvpStartFromSelection(): void {
    this.showRsvpStartChoice = false;
    this.clearRsvpHighlight();
    this.showRsvp = true;
    if (this.rsvpStartOptions.selectionText) {
      this.rsvpService.startFromSelection(this.rsvpStartOptions.selectionText);
    } else {
      this.rsvpService.startFromBeginning();
    }
  }
}
