import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {BehaviorSubject, of, Subject, Subscription} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';
import {Book} from '../../model/book.model';
import {FormsModule} from '@angular/forms';
import {InputTextModule} from 'primeng/inputtext';
import {BookService} from '../../service/book.service';
import {Button} from 'primeng/button';
import {SlicePipe} from '@angular/common';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {Router} from '@angular/router';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {HeaderFilter} from '../book-browser/filters/HeaderFilter';

@Component({
  selector: 'app-book-searcher',
  templateUrl: './book-searcher.component.html',
  imports: [
    FormsModule,
    InputTextModule,
    Button,
    SlicePipe,
    Divider,
    IconField,
    InputIcon
  ],
  styleUrls: ['./book-searcher.component.scss'],
  standalone: true
})
export class BookSearcherComponent implements OnInit, OnDestroy {
  searchQuery: string = '';
  books: Book[] = [];
  #searchSubject = new BehaviorSubject<string>('');
  #subscription!: Subscription;

  private bookService = inject(BookService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private headerFilter = new HeaderFilter(this.#searchSubject.asObservable());

  ngOnInit(): void {
    this.initializeSearch();
  }

  initializeSearch(): void {
    this.#subscription = this.bookService.bookState$.pipe(
      switchMap(bookState => this.headerFilter.filter(bookState)),
      catchError((error) => {
        console.error('Error while searching books:', error);
        return of({books: [], loaded: true, error: null});
      })
    ).subscribe({
      next: (filteredState) => {
        const term = this.searchQuery.trim();
        this.books = term.length >= 2
          ? (filteredState.books || []).slice(0, 50)
          : [];
      },
      error: (error) => console.error('Subscription error:', error)
    });
  }

  getAuthorNames(authors: string[] | undefined): string {
    return authors?.join(', ') || 'Unknown Author';
  }

  onSearchInputChange(): void {
    this.#searchSubject.next(this.searchQuery.trim());
  }

  onBookClick(book: Book): void {
    this.clearSearch();
    this.router.navigate(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.books = [];
  }

  ngOnDestroy(): void {
    if (this.#subscription) {
      this.#subscription.unsubscribe();
    }
  }
}
