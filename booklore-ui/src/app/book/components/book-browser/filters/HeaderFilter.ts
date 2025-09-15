import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {Observable, of} from 'rxjs';
import {map, debounceTime, distinctUntilChanged, switchMap} from 'rxjs/operators';

export class HeaderFilter implements BookFilter {

  constructor(private searchTerm$: Observable<any>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    const normalize = (str: string): string =>
      str.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    return this.searchTerm$.pipe(
      distinctUntilChanged(),
      switchMap(term => {
        const normalizedTerm = normalize(term || '').trim();
        if (normalizedTerm.length < 2) {
          return of(bookState);
        }
        return of(normalizedTerm).pipe(
          debounceTime(500),
          map(nTerm => {
            const filteredBooks = bookState.books?.filter(book => {
              const title = book.metadata?.title || '';
              const series = book.metadata?.seriesName || '';
              const authors = book.metadata?.authors || [];
              const categories = book.metadata?.categories || [];
              const isbn = book.metadata?.isbn10 || '';
              const isbn13 = book.metadata?.isbn13 || '';

              const matchesTitle = normalize(title).includes(nTerm);
              const matchesSeries = normalize(series).includes(nTerm);
              const matchesAuthor = authors.some(author => normalize(author).includes(nTerm));
              const matchesCategory = categories.some(category => normalize(category).includes(nTerm));
              const matchesIsbn = normalize(isbn).includes(nTerm) || normalize(isbn13).includes(nTerm);

              return matchesTitle || matchesSeries || matchesAuthor || matchesCategory || matchesIsbn;
            }) || null;

            return {...bookState, books: filteredBooks};
          })
        );
      })
    );
  }
}
