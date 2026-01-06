import {beforeEach, describe, expect, it} from 'vitest';
import {Subject} from 'rxjs';
import {HeaderFilter} from './HeaderFilter';
import {BookState} from '../../../model/state/book-state.model';

const makeBook = (overrides: any = {}) => ({
  id: 1,
  metadata: {
    bookId: 1,
    title: 'Test Title',
    authors: ['John Doe'],
    seriesName: 'Test Series',
    categories: ['Fiction'],
    isbn10: '1234567890',
    isbn13: '9781234567897',
    ...overrides.metadata
  },
  ...overrides
});

const baseState: BookState = {
  books: [
    makeBook(),
    makeBook({id: 2, metadata: {bookId: 2, title: 'Another Book', authors: ['Jane Smith'], seriesName: 'Another Series', categories: ['Nonfiction'], isbn10: '0987654321', isbn13: '9780987654321'}}),
    makeBook({id: 3, metadata: {bookId: 3, title: 'Éxample Łatin', authors: ['Åuthor Æther'], seriesName: 'Série', categories: ['Categøry'], isbn10: 'ø123', isbn13: 'æ456'}}),
    makeBook({id: 4, metadata: {bookId: 4, title: 'Special!@#$', authors: ['O\'Connor'], seriesName: '', categories: [], isbn10: '', isbn13: ''}})
  ],
  loaded: true,
  error: null
};

describe('HeaderFilter', () => {
  let search$: Subject<string>;
  let filter: HeaderFilter;

  beforeEach(() => {
    search$ = new Subject<string>();
    filter = new HeaderFilter(search$.asObservable());
  });

  it('returns original state if search term is less than 2 chars', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result).toBe(baseState);
    });
    search$.next('a');
  });

  it('filters by title (case-insensitive, normalized)', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.title).toBe('Test Title');
    });
    search$.next('test');
  });

  it('filters by author', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.authors?.[0]).toBe('Jane Smith');
    });
    search$.next('smith');
  });

  const expectBooksWithTitles = (searchTerm: string, expectedCount: number, expectedTitles: string[]) => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(expectedCount);
      const titles = result.books?.map(b => b.metadata?.title);
      expectedTitles.forEach(title => expect(titles).toContain(title));
    });
    search$.next(searchTerm);
  };

  it('filters by series', () => {
    expectBooksWithTitles('series', 2, ['Test Title', 'Another Book']);
  });

  it('filters by category', () => {
    expectBooksWithTitles('fiction', 2, ['Test Title', 'Another Book']);
  });

  it('filters by isbn10', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.isbn10).toBe('1234567890');
    });
    search$.next('1234567890');
  });

  it('filters by isbn13', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.isbn13).toBe('9780987654321');
    });
    search$.next('9780987654321');
  });

  it('normalizes diacritics and special chars', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.title).toBe('Éxample Łatin');
    });
    search$.next('example latin');
  });

  it('normalizes special Unicode chars in categories (ø -> o)', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.categories?.[0]).toBe('Categøry');
    });
    search$.next('category');
  });

  it('removes punctuation and matches', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0]?.metadata?.title).toBe('Special!@#$');
    });
    search$.next('special');
  });

  it('returns empty books if no match', () => {
    filter.filter(baseState).subscribe(result => {
      expect(result.books).toEqual([]);
    });
    search$.next('notfound');
  });

  it('handles undefined fields gracefully', () => {
    const state: BookState = {
      books: [
        {
          id: 5,
          bookType: 'EPUB',
          libraryId: 1,
          libraryName: 'Test Library',
          metadata: {
            bookId: 5,
            title: undefined,
            authors: undefined,
            seriesName: undefined,
            categories: undefined,
            isbn10: undefined,
            isbn13: undefined
          }
        }
      ],
      loaded: true,
      error: null
    };
    filter.filter(state).subscribe(result => {
      expect(result.books).toEqual([]);
    });
    search$.next('anything');
  });

  it('distinctUntilChanged prevents duplicate emissions for same search term', async () => {
    const emissions: any[] = [];
    const subscription = filter.filter(baseState).subscribe(result => {
      emissions.push(result);
    });
    search$.next('test');
    search$.next('test');
    await new Promise(resolve => setTimeout(resolve, 600));
    expect(emissions.length).toBe(1);
    subscription.unsubscribe();
  });
});
