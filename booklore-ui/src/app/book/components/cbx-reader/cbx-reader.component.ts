import {Component, HostListener, inject, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CbxReaderService} from '../../service/cbx-reader.service';
import {BookService} from '../../service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {forkJoin} from 'rxjs';
import {BookSetting, BookType, CbxPageSpread, CbxPageViewMode, PdfPageSpread, PdfPageViewMode} from '../../model/book.model';
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

  backgroundColor: 'black' | 'gray' | 'white' = 'gray';

  private touchStartX = 0;
  private touchEndX = 0;

  private route = inject(ActivatedRoute);
  private cbxReaderService = inject(CbxReaderService);
  private pdfReaderService = inject(NewPdfReaderService);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);

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

          const pagesObservable = this.bookType === 'PDF'
            ? this.pdfReaderService.getAvailablePages(this.bookId)
            : this.cbxReaderService.getAvailablePages(this.bookId);

          pagesObservable.subscribe({
            next: (pages) => {

              this.pages = pages;
              if (this.bookType === 'CBX') {
                const global = userSettings.perBookSetting.cbx === 'Global';
                this.pageViewMode = global
                  ? userSettings.cbxReaderSetting.pageViewMode || CbxPageViewMode.SINGLE_PAGE
                  : bookSettings.cbxSettings?.pageViewMode || userSettings.cbxReaderSetting.pageViewMode || CbxPageViewMode.SINGLE_PAGE;

                this.pageSpread = global
                  ? userSettings.cbxReaderSetting.pageSpread || CbxPageSpread.ODD
                  : bookSettings.cbxSettings?.pageSpread || userSettings.cbxReaderSetting.pageSpread || CbxPageSpread.ODD;

                this.currentPage = (book.cbxProgress?.page || 1) - 1;
              }

              if (this.bookType === 'PDF') {
                const global = userSettings.perBookSetting.pdf === 'Global';
                this.pageViewMode = global
                  ? userSettings.newPdfReaderSetting.pageViewMode || PdfPageViewMode.SINGLE_PAGE
                  : bookSettings.newPdfSettings?.pageViewMode || userSettings.newPdfReaderSetting.pageViewMode || PdfPageViewMode.SINGLE_PAGE;

                this.pageSpread = global
                  ? userSettings.newPdfReaderSetting.pageSpread || PdfPageSpread.ODD
                  : bookSettings.newPdfSettings?.pageSpread || userSettings.newPdfReaderSetting.pageSpread || PdfPageSpread.ODD;

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
    return this.pageViewMode === CbxPageViewMode.TWO_PAGE || this.pageViewMode === PdfPageViewMode.TWO_PAGE;
  }

  get backgroundColorIcon(): string {
    switch (this.backgroundColor) {
      case 'black': return '⚫';
      case 'gray': return '🔘';
      case 'white': return '⚪';
      default: return '🔘';
    }
  }

  toggleBackground(): void {
    switch (this.backgroundColor) {
      case 'black':
        this.backgroundColor = 'gray';
        break;
      case 'gray':
        this.backgroundColor = 'white';
        break;
      case 'white':
        this.backgroundColor = 'black';
        break;
    }
  }

  toggleView() {
    if (!this.isTwoPageView && this.isPhonePortrait()) return;
    this.pageViewMode = this.isTwoPageView ? (this.bookType === "CBX" ? CbxPageViewMode.SINGLE_PAGE : PdfPageViewMode.SINGLE_PAGE) : (this.bookType === "CBX" ? CbxPageViewMode.TWO_PAGE : PdfPageViewMode.TWO_PAGE);
    this.alignCurrentPageToParity();
    this.updateViewerSetting();
  }

  toggleSpreadDirection() {
    if (this.pageSpread === CbxPageSpread.ODD || this.pageSpread === PdfPageSpread.ODD) {
      this.pageSpread = this.bookType === "CBX" ? CbxPageSpread.EVEN : PdfPageSpread.EVEN;
    } else {
      this.pageSpread = this.bookType === "CBX" ? CbxPageSpread.ODD : PdfPageSpread.ODD;
    }
    this.alignCurrentPageToParity();
    this.updateViewerSetting();
  }

  nextPage() {
    const previousPage = this.currentPage;

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
      this.pageViewMode = this.bookType === "CBX" ? CbxPageViewMode.SINGLE_PAGE : PdfPageViewMode.SINGLE_PAGE;
      this.updateViewerSetting();
    }
  }

  private isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  private getPageImageUrl(pageIndex: number): string {
    return this.bookType === 'PDF'
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

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = this.bookType === "CBX"
      ? {
        cbxSettings: {
          pageSpread: this.pageSpread as CbxPageSpread,
          pageViewMode: this.pageViewMode as CbxPageViewMode,
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

    if (this.bookType === 'CBX') {
      this.bookService.saveCbxProgress(this.bookId, this.currentPage + 1, percentage).subscribe();
    }
    if (this.bookType === 'PDF') {
      this.bookService.savePdfProgress(this.bookId, this.currentPage + 1, percentage).subscribe();
    }
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.pages.length) return;

    const targetIndex = page - 1;
    if (targetIndex === this.currentPage) return;

    this.currentPage = targetIndex;
    this.alignCurrentPageToParity();
    this.updateProgress();
  }
}
