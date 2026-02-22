import {AfterViewChecked, Component, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {AsyncPipe, NgClass, NgStyle} from '@angular/common';
import {combineLatest, Observable} from 'rxjs';
import {filter, map} from 'rxjs/operators';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Button} from 'primeng/button';
import {Tag} from 'primeng/tag';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
import {MessageService} from 'primeng/api';
import {Tooltip} from 'primeng/tooltip';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails} from '../../model/author.model';
import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {BookCardComponent} from '../../../book/components/book-browser/book-card/book-card.component';
import {CoverScalePreferenceService} from '../../../book/components/book-browser/cover-scale-preference.service';
import {BookCardOverlayPreferenceService} from '../../../book/components/book-browser/book-card-overlay-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import {AuthorMatchComponent} from '../author-match/author-match.component';
import {AuthorEditorComponent} from '../author-editor/author-editor.component';
import {PageTitleService} from '../../../../shared/service/page-title.service';

@Component({
  selector: 'app-author-detail',
  standalone: true,
  templateUrl: './author-detail.component.html',
  styleUrls: ['./author-detail.component.scss'],
  imports: [
    AsyncPipe,
    NgClass,
    NgStyle,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    ProgressSpinner,
    Button,
    Tag,
    TranslocoDirective,
    Tooltip,
    VirtualScrollerModule,
    BookCardComponent,
    AuthorMatchComponent,
    AuthorEditorComponent
  ]
})
export class AuthorDetailComponent implements OnInit, AfterViewChecked {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authorService = inject(AuthorService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected userService = inject(UserService);
  private pageTitle = inject(PageTitleService);
  private t = inject(TranslocoService);

  @ViewChild('descriptionContent') descriptionContentRef?: ElementRef<HTMLElement>;

  author: AuthorDetails | null = null;
  loading = true;
  tab = 'books';
  isExpanded = false;
  isOverflowing = false;
  hasPhoto = true;
  photoTimestamp = Date.now();
  quickMatching = false;

  authorBooks$!: Observable<Book[]>;

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  get photoUrl(): string {
    if (!this.author) return '';
    return this.authorService.getAuthorPhotoUrl(this.author.id) + '&t=' + this.photoTimestamp;
  }

  get canEditMetadata(): boolean {
    const user = this.userService.getCurrentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  ngOnInit(): void {
    const authorId = Number(this.route.snapshot.paramMap.get('authorId'));
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam) {
      this.tab = tabParam;
    }
    this.loadAuthor(authorId);

    this.authorBooks$ = this.bookService.bookState$.pipe(
      filter(state => state.loaded && !!state.books),
      map(state => {
        const books = state.books || [];
        const authorName = this.author?.name?.toLowerCase();
        if (!authorName) return [];
        return books.filter(b =>
          b.metadata?.authors?.some(a => a.toLowerCase() === authorName)
        );
      })
    );
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

  onPhotoError(): void {
    this.hasPhoto = false;
  }

  onAuthorUpdated(updatedAuthor: AuthorDetails): void {
    this.author = updatedAuthor;
    this.hasPhoto = true;
    this.photoTimestamp = Date.now();
  }

  quickMatch(): void {
    if (!this.author || this.quickMatching) return;
    this.quickMatching = true;
    this.authorService.quickMatchAuthor(this.author.id).subscribe({
      next: (matched) => {
        this.onAuthorUpdated(matched);
        this.quickMatching = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.quickMatchSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchSuccessDetail')
        });
      },
      error: () => {
        this.quickMatching = false;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.quickMatchFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchFailedDetail')
        });
      }
    });
  }

  private loadAuthor(authorId: number): void {
    this.authorService.getAuthorDetails(authorId).subscribe({
      next: (author) => {
        this.author = author;
        this.loading = false;
        this.pageTitle.setPageTitle(author.name);

        this.authorBooks$ = this.bookService.bookState$.pipe(
          filter(state => state.loaded && !!state.books),
          map(state => {
            const books = state.books || [];
            const name = author.name.toLowerCase();
            return books.filter(b =>
              b.metadata?.authors?.some(a => a.toLowerCase() === name)
            );
          })
        );
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
