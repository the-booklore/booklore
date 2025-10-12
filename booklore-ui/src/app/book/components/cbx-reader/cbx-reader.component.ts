import {Component, HostListener, inject, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CbxReaderService} from '../../service/cbx-reader.service';
import {BookService} from '../../service/book.service';
import {
  CbxFitMode,
  CbxPageSpread,
  CbxPageViewMode,
  CbxScrollMode,
  PdfPageSpread,
  PdfPageViewMode,
  UserService,
  CbxBackgroundColor
} from '../../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {forkJoin} from 'rxjs';
import {BookSetting, BookType} from '../../model/book.model';
import {ProgressSpinner} from 'primeng/progressspinner';
import {FormsModule} from "@angular/forms";
import {NewPdfReaderService} from '../../service/new-pdf-reader.service';

@Component({
  selector: 'app-cbx-reader',
  standalone: true,
  imports: [ProgressSpinner, FormsModule],
  templateUrl: './cbx-reader.component.html',
  styleUrl: './cbx-reader.component.scss'
})
export class CbxReaderComponent implements OnInit {
  bookType!: BookType;

  goToPageInput: number | null = null;
  bookId!: number;
  pages: number[] = [];
  currentPage = 0;
  isLoading = true;

  pageSpread: CbxPageSpread | PdfPageSpread = CbxPageSpread.ODD;
  pageViewMode: CbxPageViewMode | PdfPageViewMode = CbxPageViewMode.SINGLE_PAGE;
  backgroundColor: CbxBackgroundColor = CbxBackgroundColor.GRAY;
  fitMode: CbxFitMode = CbxFitMode.FIT_PAGE;

  private touchStartX = 0;
  private touchEndX = 0;

  private route = inject(ActivatedRoute);
  private cbxReaderService = inject(CbxReaderService);
  private pdfReaderService = inject(NewPdfReaderService);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);


  showFitModeDropdown: boolean = false;
  showMobileOptionsDropdown: boolean = false;
  showFitModeSubmenu: boolean = false;

  fitModeOptions = [
    {value: CbxFitMode.FIT_PAGE, label: 'Fit Page', icon: '⬜'},
    {value: CbxFitMode.FIT_WIDTH, label: 'Fit Width', icon: '↔️'},
    {value: CbxFitMode.FIT_HEIGHT, label: 'Fit Height', icon: '↕️'},
    {value: CbxFitMode.ACTUAL_SIZE, label: 'Actual Size', icon: '1:1'},
    {value: CbxFitMode.AUTO, label: 'Automatic', icon: '🔄'}
  ];

  scrollMode: CbxScrollMode = CbxScrollMode.PAGINATED;

  infiniteScrollPages: number[] = [];
  preloadCount: number = 3;
  isLoadingMore: boolean = false;

  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;

  private static readonly TYPE_PDF = 'PDF';
  private static readonly TYPE_CBX = 'CBX';
  private static readonly SETTING_GLOBAL = 'Global';

  ngOnInit() {
    this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      this.bookId = +params.get('bookId')!;

      forkJoin([
        this.bookService.getBookByIdFromAPI(this.bookId, false),
        this.bookService.getBookSetting(this.bookId),
        this.userService.getMyself()
      ]).subscribe({
        next: ([book, bookSettings, myself]) => {
          const userSettings = myself.userSettings;
          this.bookType = book.bookType;

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
              this.isLoading = false;
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

  get isTwoPageView(): boolean {
    return this.pageViewMode === this.CbxPageViewMode.TWO_PAGE || this.pageViewMode === PdfPageViewMode.TWO_PAGE;
  }

  get backgroundColorIcon(): string {
    switch (this.backgroundColor) {
      case CbxBackgroundColor.BLACK:
        return '⚫';
      case CbxBackgroundColor.GRAY:
        return '🔘';
      case CbxBackgroundColor.WHITE:
        return '⚪';
      default:
        return '🔘';
    }
  }

  toggleBackground(): void {
    switch (this.backgroundColor) {
      case CbxBackgroundColor.BLACK:
        this.backgroundColor = CbxBackgroundColor.GRAY;
        break;
      case CbxBackgroundColor.GRAY:
        this.backgroundColor = CbxBackgroundColor.WHITE;
        break;
      case CbxBackgroundColor.WHITE:
        this.backgroundColor = CbxBackgroundColor.BLACK;
        break;
    }
    this.updateViewerSetting();
  }

  toggleView() {
    if (!this.isTwoPageView && this.isPhonePortrait()) return;
    this.pageViewMode = this.isTwoPageView
      ? (this.bookType === CbxReaderComponent.TYPE_CBX ? this.CbxPageViewMode.SINGLE_PAGE : PdfPageViewMode.SINGLE_PAGE)
      : (this.bookType === CbxReaderComponent.TYPE_CBX ? this.CbxPageViewMode.TWO_PAGE : PdfPageViewMode.TWO_PAGE);
    this.alignCurrentPageToParity();
    this.updateViewerSetting();
  }

  toggleSpreadDirection() {
    if (this.pageSpread === this.CbxPageSpread.ODD || this.pageSpread === PdfPageSpread.ODD) {
      this.pageSpread = this.bookType === CbxReaderComponent.TYPE_CBX ? this.CbxPageSpread.EVEN : PdfPageSpread.EVEN;
    } else {
      this.pageSpread = this.bookType === CbxReaderComponent.TYPE_CBX ? this.CbxPageSpread.ODD : PdfPageSpread.ODD;
    }
    this.alignCurrentPageToParity();
    this.updateViewerSetting();
  }

  toggleScrollMode(): void {
    this.scrollMode = this.scrollMode === CbxScrollMode.PAGINATED
      ? CbxScrollMode.INFINITE
      : CbxScrollMode.PAGINATED;

    this.updateViewerSetting();

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.initializeInfiniteScroll();
      setTimeout(() => this.scrollToPage(this.currentPage), 100);
    }
  }

  nextPage() {
    const previousPage = this.currentPage;

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      if (this.currentPage < this.pages.length - 1) {
        this.currentPage++;
        this.scrollToPage(this.currentPage);
        this.updateProgress();
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
    }
  }

  previousPage() {
    if (this.scrollMode === CbxScrollMode.INFINITE) {
      if (this.currentPage > 0) {
        this.currentPage--;
        this.scrollToPage(this.currentPage);
        this.updateProgress();
      }
      return;
    }

    if (this.isTwoPageView) {
      this.currentPage = Math.max(0, this.currentPage - 2);
    } else {
      this.currentPage = Math.max(0, this.currentPage - 1);
    }
    this.updateProgress();
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

  toggleFitModeDropdown(): void {
    this.showFitModeDropdown = !this.showFitModeDropdown;
  }

  selectFitMode(mode: CbxFitMode): void {
    this.fitMode = mode;
    this.showFitModeDropdown = false;
    this.updateViewerSetting();
  }

  toggleMobileOptionsDropdown(): void {
    this.showMobileOptionsDropdown = !this.showMobileOptionsDropdown;
    this.showFitModeSubmenu = false;
  }

  selectMobileOption(option: string): void {
    if (option === 'fitMode') {
      this.showFitModeSubmenu = !this.showFitModeSubmenu;
    }
  }

  selectFitModeFromMobile(mode: CbxFitMode): void {
    this.fitMode = mode;
    this.showFitModeSubmenu = false;
    this.showMobileOptionsDropdown = false;
    this.updateViewerSetting();
  }

  toggleScrollModeFromMobile(): void {
    this.toggleScrollMode();
    this.showMobileOptionsDropdown = false;
  }

  toggleSpreadDirectionFromMobile(): void {
    this.toggleSpreadDirection();
    this.showMobileOptionsDropdown = false;
  }

  toggleViewFromMobile(): void {
    this.toggleView();
    this.showMobileOptionsDropdown = false;
  }

  toggleBackgroundFromMobile(): void {
    this.toggleBackground();
    this.showMobileOptionsDropdown = false;
  }

  get displayLabel(): string {
    const option = this.fitModeOptions.find(opt => opt.value === this.fitMode);
    return option ? option.icon : '↔️';
  }

  get scrollModeIcon(): string {
    return this.scrollMode === CbxScrollMode.PAGINATED ? '📄' : '📜';
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

  goToPage(page: number): void {
    if (page < 1 || page > this.pages.length) return;

    const targetIndex = page - 1;
    if (targetIndex === this.currentPage) return;

    this.currentPage = targetIndex;

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.ensurePageLoaded(targetIndex);
      this.scrollToPage(targetIndex);
      this.updateProgress();
    } else {
      this.alignCurrentPageToParity();
      this.updateProgress();
    }
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
    this.enforcePortraitSinglePageView();
  }

  private handleSwipeGesture() {
    const delta = this.touchEndX - this.touchStartX;
    if (Math.abs(delta) >= 50) delta < 0 ? this.nextPage() : this.previousPage();
  }

  private enforcePortraitSinglePageView() {
    if (this.isPhonePortrait() && this.isTwoPageView) {
      this.pageViewMode = this.bookType === CbxReaderComponent.TYPE_CBX ? CbxPageViewMode.SINGLE_PAGE : PdfPageViewMode.SINGLE_PAGE;
      this.updateViewerSetting();
    }
  }

  private isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.fit-mode-dropdown')) {
      this.showFitModeDropdown = false;
    }
    if (!target.closest('.mobile-controls')) {
      this.showMobileOptionsDropdown = false;
      this.showFitModeSubmenu = false;
    }
  }
}
