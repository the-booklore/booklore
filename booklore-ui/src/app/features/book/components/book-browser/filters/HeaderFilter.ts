import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {Observable, of} from 'rxjs';
import {map, debounceTime, distinctUntilChanged, switchMap} from 'rxjs/operators';

export class HeaderFilter implements BookFilter {

  constructor(private searchTerm$: Observable<string>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    const normalize = (str: string): string => {
      if (!str) return '';
      // Normalize Unicode combining characters (e.g., é -> e)
      let s = str.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
      s = s.replace(/ø/gi, 'o')
           .replace(/ł/gi, 'l')
           .replace(/æ/gi, 'ae')
           .replace(/œ/gi, 'oe')
           .replace(/ß/g, 'ss');
      s = s.replace(/[!@$%^&*_=|~`<>?/";']/g, '');
      s = s.replace(/\s+/g, ' ').trim();
      return s.toLowerCase();
    };
    
    const normalizeAuthorNameForSearch = (author: string): string[] => {
      // Convert "LastName, FirstName" to "FirstName LastName" if in comma format
      const commaPattern = /,\s*/;
      let normalizedAuthor = normalize(author);
      
      const results = [normalizedAuthor];
      
      // If in comma format, also add the converted format
      if (commaPattern.test(author)) {
        const parts = author.split(commaPattern);
        if (parts.length >= 2) {
          const firstName = normalize(parts[1].trim());
          const lastName = normalize(parts[0].trim());
          results.push(`${firstName} ${lastName}`.trim());
        }
      } else if (normalizedAuthor.includes(' ')) {
        // If in "FirstName LastName" format, also add the comma format for matching
        const authorParts = normalizedAuthor.split(' ');
        if (authorParts.length >= 2) {
          const firstName = authorParts.slice(0, -1).join(' ');
          const lastName = authorParts[authorParts.length - 1];
          results.push(`${lastName}, ${firstName}`.trim());
        }
      }
      
      return results;
    };

    return this.searchTerm$.pipe(
      distinctUntilChanged(),
      switchMap((term: string) => {
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
              const matchesAuthor = authors.some(author => {
                const authorFormats = normalizeAuthorNameForSearch(author);
                return authorFormats.some(format => format.includes(nTerm));
              });
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
