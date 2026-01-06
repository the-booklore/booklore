import {beforeEach, describe, expect, it} from 'vitest';
import {BehaviorSubject, firstValueFrom} from 'rxjs';
import {doesBookMatchReadStatus, isFileSizeInRange, isMatchScoreInRange, isPageCountInRange, isRatingInRange, isRatingInRange10, SideBarFilter} from './SidebarFilter';
import {BookState} from '../../../model/state/book-state.model';
import {Book, ReadStatus} from '../../../model/book.model';
import {Shelf} from '../../../model/shelf.model';

describe('SidebarFilter helpers', () => {
  it('isRatingInRange returns true for value in range', () => {
    expect(isRatingInRange(3.0, '3to4')).toBe(true);
    expect(isRatingInRange(3.99, '3to4')).toBe(true);
    expect(isRatingInRange(4.0, '3to4')).toBe(false);
  });
  it('isRatingInRange returns false for value out of range', () => {
    expect(isRatingInRange(4.5, '3to4')).toBe(false);
    expect(isRatingInRange(2.9, '3to4')).toBe(false);
  });
  it('isRatingInRange10 matches rounded rating', () => {
    expect(isRatingInRange10(7.2, '7')).toBe(true);
    expect(isRatingInRange10(7.7, '8')).toBe(true);
    expect(isRatingInRange10(7.2, '8')).toBe(false);
  });
  it('isFileSizeInRange returns true for value in range', () => {
    expect(isFileSizeInRange(0, '<1mb')).toBe(true);
    expect(isFileSizeInRange(1023, '<1mb')).toBe(true);
    expect(isFileSizeInRange(1024, '<1mb')).toBe(false);
  });
  it('isFileSizeInRange returns false for value out of range', () => {
    expect(isFileSizeInRange(2000, '<1mb')).toBe(false);
    expect(isFileSizeInRange(-1, '<1mb')).toBe(false);
  });
  it('isPageCountInRange returns true for value in range', () => {
    expect(isPageCountInRange(100, '100to200')).toBe(true);
    expect(isPageCountInRange(199, '100to200')).toBe(true);
    expect(isPageCountInRange(200, '100to200')).toBe(false);
  });
  it('isPageCountInRange returns false for value out of range', () => {
    expect(isPageCountInRange(50, '100to200')).toBe(false);
    expect(isPageCountInRange(200, '100to200')).toBe(false);
  });
  it('isMatchScoreInRange returns true for value in range', () => {
    expect(isMatchScoreInRange(0.80, '0.80-0.89')).toBe(true);
    expect(isMatchScoreInRange(0.89, '0.80-0.89')).toBe(true);
    expect(isMatchScoreInRange(0.90, '0.80-0.89')).toBe(false);
    expect(isMatchScoreInRange(85, '0.80-0.89')).toBe(true);
  });
  it('isMatchScoreInRange returns false for value out of range', () => {
    expect(isMatchScoreInRange(0.5, '0.80-0.89')).toBe(false);
    expect(isMatchScoreInRange(0.95, '0.80-0.89')).toBe(false);
  });
  it('doesBookMatchReadStatus returns true if status matches', () => {
    const book = {readStatus: ReadStatus.READ} as Book;
    expect(doesBookMatchReadStatus(book, [ReadStatus.READ])).toBe(true);
  });
  it('doesBookMatchReadStatus returns false if status does not match', () => {
    const book = {readStatus: ReadStatus.UNREAD} as Book;
    expect(doesBookMatchReadStatus(book, [ReadStatus.READ])).toBe(false);
  });
  it('doesBookMatchReadStatus returns true for unset if included', () => {
    const book = {} as Book;
    expect(doesBookMatchReadStatus(book, [ReadStatus.UNSET])).toBe(true);
  });
  it('isRatingInRange returns false for unknown rangeId', () => {
    expect(isRatingInRange(3.5, 'not-a-range')).toBe(false);
  });
  it('isRatingInRange returns false for null/undefined rating', () => {
    expect(isRatingInRange(undefined, '3to4')).toBe(false);
    expect(isRatingInRange(null, '3to4')).toBe(false);
  });
  it('isRatingInRange10 returns false for null/undefined rating', () => {
    expect(isRatingInRange10(undefined, '7')).toBe(false);
    expect(isRatingInRange10(null, '7')).toBe(false);
  });
  it('isFileSizeInRange returns false for unknown rangeId', () => {
    expect(isFileSizeInRange(500, 'not-a-range')).toBe(false);
  });
  it('isFileSizeInRange returns false for null/undefined fileSize', () => {
    expect(isFileSizeInRange(undefined, '<1mb')).toBe(false);
  });
  it('isPageCountInRange returns false for unknown rangeId', () => {
    expect(isPageCountInRange(150, 'not-a-range')).toBe(false);
  });
  it('isPageCountInRange returns false for null/undefined pageCount', () => {
    expect(isPageCountInRange(undefined, '100to200')).toBe(false);
  });
  it('isMatchScoreInRange returns false for unknown rangeId', () => {
    expect(isMatchScoreInRange(0.85, 'not-a-range')).toBe(false);
  });
  it('isMatchScoreInRange returns false for null/undefined score', () => {
    expect(isMatchScoreInRange(undefined, '0.80-0.89')).toBe(false);
    expect(isMatchScoreInRange(null, '0.80-0.89')).toBe(false);
  });
  it('doesBookMatchReadStatus returns false if selected is empty', () => {
    const book = {readStatus: ReadStatus.READ} as Book;
    expect(doesBookMatchReadStatus(book, [])).toBe(false);
  });
  it('doesBookMatchReadStatus returns false if status is not in selected', () => {
    const book = {readStatus: ReadStatus.READING} as Book;
    expect(doesBookMatchReadStatus(book, [ReadStatus.READ])).toBe(false);
  });
});

describe('SideBarFilter', () => {
  let books: Book[];
  let bookState: BookState;

  beforeEach(() => {
    // Create fresh book objects for each test to prevent mutation pollution
    books = [
      {
        id: 1,
        libraryId: 1,
        libraryName: 'Lib1',
        metadata: {
          bookId: 1,
          authors: ['A1'],
          categories: ['C1'],
          moods: ['M1'],
          tags: ['T1'],
          publisher: 'P1',
          seriesName: 'S1',
          amazonRating: 4.2,
          goodreadsRating: 3.8,
          hardcoverRating: 4.0,
          publishedDate: '2020-01-01',
          pageCount: 150,
          language: 'en'
        },
        personalRating: 7,
        fileSizeKb: 500,
        shelves: [{name: 'shelf1', icon: 'icon1'}] as Shelf[],
        metadataMatchScore: 0.8,
        bookType: 'EPUB',
        readStatus: ReadStatus.READ
      } as Book,
      {
        id: 2,
        libraryId: 1,
        libraryName: 'Lib1',
        metadata: {
          bookId: 2,
          authors: ['A2'],
          categories: ['C2'],
          moods: ['M2'],
          tags: ['T2'],
          publisher: 'P2',
          seriesName: 'S2',
          amazonRating: 2.5,
          goodreadsRating: 2.8,
          hardcoverRating: 2.0,
          publishedDate: '2019-01-01',
          pageCount: 50,
          language: 'fr'
        },
        personalRating: 3,
        fileSizeKb: 2000,
        shelves: [] as Shelf[],
        metadataMatchScore: 0.5,
        bookType: 'PDF',
        readStatus: ReadStatus.UNREAD
      } as Book
    ];
    bookState = {books, loaded: true, error: null};
  });

  function filterWith(activeFilters: any, mode: 'and' | 'or' = 'and') {
    const filter$ = new BehaviorSubject(activeFilters);
    const mode$ = new BehaviorSubject(mode);
    const filter = new SideBarFilter(filter$, mode$);
    return filter.filter(bookState);
  }

  it('filters by author (and mode)', async () => {
    const result = await firstValueFrom(filterWith({author: ['A1']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.authors).toContain('A1');
  });

  it('filters by author (or mode)', async () => {
    const result = await firstValueFrom(filterWith({author: ['A1', 'A2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by category', async () => {
    const result = await firstValueFrom(filterWith({category: ['C2']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.categories).toContain('C2');
  });

  it('filters by mood', async () => {
    const result = await firstValueFrom(filterWith({mood: ['M1']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.moods).toContain('M1');
  });

  it('filters by tag', async () => {
    const result = await firstValueFrom(filterWith({tag: ['T2']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.tags).toContain('T2');
  });

  it('filters by publisher', async () => {
    const result = await firstValueFrom(filterWith({publisher: ['P1']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.publisher).toBe('P1');
  });

  it('filters by series', async () => {
    const result = await firstValueFrom(filterWith({series: ['S2']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.seriesName).toBe('S2');
  });

  it('filters by readStatus', async () => {
    const result = await firstValueFrom(filterWith({readStatus: [ReadStatus.READ]}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.readStatus).toBe(ReadStatus.READ);
  });

  it('filters by amazonRating', async () => {
    const result = await firstValueFrom(filterWith({amazonRating: ['4to4.5']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.amazonRating).toBeGreaterThanOrEqual(4);
  });

  it('filters by goodreadsRating', async () => {
    const result = await firstValueFrom(filterWith({goodreadsRating: ['3to4']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.goodreadsRating).toBeGreaterThanOrEqual(3);
  });

  it('filters by hardcoverRating', async () => {
    const result = await firstValueFrom(filterWith({hardcoverRating: ['2to3']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.hardcoverRating).toBeGreaterThanOrEqual(2);
  });

  it('filters by personalRating', async () => {
    const result = await firstValueFrom(filterWith({personalRating: ['7']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.personalRating).toBe(7);
  });

  it('filters by shelfStatus unshelved', async () => {
    const result = await firstValueFrom(filterWith({shelfStatus: ['unshelved']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.shelves?.length).toBe(0);
  });

  it('filters by fileSize', async () => {
    const result = await firstValueFrom(filterWith({fileSize: ['<1mb']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.fileSizeKb).toBeLessThan(1024);
  });

  it('filters by language', async () => {
    const result = await firstValueFrom(filterWith({language: ['fr']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.language).toBe('fr');
  });

  it('filters by pageCount', async () => {
    const result = await firstValueFrom(filterWith({pageCount: ['100to200']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadata?.pageCount).toBeGreaterThanOrEqual(100);
  });

  it('filters by matchScore', async () => {
    const result = await firstValueFrom(filterWith({matchScore: ['0.80-0.89']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.metadataMatchScore).toBeGreaterThanOrEqual(0.8);
  });

  it('filters by bookType', async () => {
    const result = await firstValueFrom(filterWith({bookType: ['PDF']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.bookType).toBe('PDF');
  });

  it('returns all books if no filters', async () => {
    const result = await firstValueFrom(filterWith({}, 'and'));
    expect(result?.books?.length).toBe(2);
  });

  it('returns all books if filters is null', async () => {
    const filter$ = new BehaviorSubject(null);
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(bookState));
    expect(result?.books?.length).toBe(2);
  });

  it('returns original state if books is null', async () => {
    bookState.books = null as any;
    const result = await firstValueFrom(filterWith({author: ['A1']}, 'and'));
    expect(result).toBe(bookState);
    expect(result.books).toBeNull();
  });

  it('returns original state if books is undefined', async () => {
    bookState.books = undefined as any;
    const result = await firstValueFrom(filterWith({author: ['A1']}, 'and'));
    expect(result).toBe(bookState);
    expect(result.books).toBeUndefined();
  });

  it('returns empty books array for empty bookState', async () => {
    bookState.books = [];
    const result = await firstValueFrom(filterWith({author: ['A1']}, 'and'));
    expect(result?.books?.length).toBe(0);
  });

  it('filters by multiple authors in OR mode', async () => {
    const result = await firstValueFrom(filterWith({author: ['A1', 'A2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple categories in OR mode', async () => {
    const result = await firstValueFrom(filterWith({category: ['C1', 'C2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple moods in OR mode', async () => {
    const result = await firstValueFrom(filterWith({mood: ['M1', 'M2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple tags in OR mode', async () => {
    const result = await firstValueFrom(filterWith({tag: ['T1', 'T2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple publishers in OR mode', async () => {
    const result = await firstValueFrom(filterWith({publisher: ['P1', 'P2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple series in OR mode', async () => {
    const result = await firstValueFrom(filterWith({series: ['S1', 'S2']}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('returns true for empty filter values in OR mode', async () => {
    const result = await firstValueFrom(filterWith({author: []}, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('returns false for empty filter values in AND mode', async () => {
    const result = await firstValueFrom(filterWith({author: []}, 'and'));
    expect(result?.books?.length).toBe(0);
  });

  it('filters with multiple filter types in AND mode', async () => {
    const result = await firstValueFrom(filterWith({
      author: ['A1'],
      category: ['C1'],
      readStatus: [ReadStatus.READ]
    }, 'and'));
    expect(result?.books?.length).toBe(1);
  });

  it('filters with multiple filter types in OR mode', async () => {
    const result = await firstValueFrom(filterWith({
      author: ['A1'],
      category: ['C2']
    }, 'or'));
    expect(result?.books?.length).toBe(2);
  });

  it('returns false for unknown filter type', async () => {
    const result = await firstValueFrom(filterWith({unknownFilter: ['value']}, 'and'));
    expect(result?.books?.length).toBe(0);
  });

  it('filters by shelfStatus shelved', async () => {
    const result = await firstValueFrom(filterWith({shelfStatus: ['shelved']}, 'and'));
    expect(result?.books?.length).toBe(1);
    expect(result?.books?.[0]?.shelves?.length).toBeGreaterThan(0);
  });

  it('returns false for book without publishedDate', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, publishedDate: undefined}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({publishedDate: [2020]});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    expect(result?.books?.length).toBe(0);
  });

  it('filters by multiple authors in AND mode - all must match', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, authors: ['A1', 'A3']}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({author: ['A1', 'A3']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    expect(result?.books?.length).toBe(1);
  });

  it('filters by multiple categories in AND mode - all must match', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, categories: ['C1', 'C3']}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({category: ['C1', 'C3']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    expect(result?.books?.length).toBe(1);
  });

  it('filters by multiple moods in AND mode - all must match', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, moods: ['M1', 'M3']}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({mood: ['M1', 'M3']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    expect(result?.books?.length).toBe(1);
  });

  it('filters by multiple tags in AND mode - all must match', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, tags: ['T1', 'T3']}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({tag: ['T1', 'T3']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    expect(result?.books?.length).toBe(1);
  });

  it('filters by multiple publishers in AND mode - returns books matching all', async () => {
    // Publisher can only have one value, so AND with multiple should return nothing
    const result = await firstValueFrom(filterWith({publisher: ['P1', 'P2']}, 'and'));
    expect(result?.books?.length).toBe(0);
  });

  it('filters by multiple series in AND mode - returns books matching all', async () => {
    // Series can only have one value, so AND with multiple should return nothing
    const result = await firstValueFrom(filterWith({series: ['S1', 'S2']}, 'and'));
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null metadata', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: null as any},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({author: ['A1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null metadata, so it doesn't match. Book 2 has author 'A2', doesn't match 'A1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with undefined metadata', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: undefined as any},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({author: ['A1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has undefined metadata, so it doesn't match. Book 2 has author 'A2', doesn't match 'A1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null authors array', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, authors: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({author: ['A1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null authors, so it doesn't match. Book 2 has author 'A2', doesn't match 'A1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with undefined authors array', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, authors: undefined as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({author: ['A1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has undefined authors, so it doesn't match. Book 2 has author 'A2', doesn't match 'A1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null categories array', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, categories: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({category: ['C1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null categories, so it doesn't match. Book 2 has category 'C2', doesn't match 'C1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null moods array', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, moods: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({mood: ['M1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null moods, so it doesn't match. Book 2 has mood 'M2', doesn't match 'M1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null tags array', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, tags: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({tag: ['T1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null tags, so it doesn't match. Book 2 has tag 'T2', doesn't match 'T1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null publisher', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, publisher: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({publisher: ['P1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null publisher, so it doesn't match. Book 2 has publisher 'P2', doesn't match 'P1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null series', async () => {
    const testBooks: Book[] = [
      {...books[0], metadata: {...books[0].metadata!, seriesName: null as any}},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({series: ['S1']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null series, so it doesn't match. Book 2 has series 'S2', doesn't match 'S1'
    expect(result?.books?.length).toBe(0);
  });

  it('handles book with null shelves', async () => {
    const testBooks: Book[] = [
      {...books[0], shelves: null as any},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({shelfStatus: ['unshelved']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has null shelves (unshelved), book 2 has empty shelves array (also unshelved)
    expect(result?.books?.length).toBe(2);
  });

  it('handles book with undefined shelves', async () => {
    const testBooks: Book[] = [
      {...books[0], shelves: undefined as any},
      {...books[1]}
    ];
    const testState = {...bookState, books: testBooks};
    const filter$ = new BehaviorSubject({shelfStatus: ['unshelved']});
    const mode$ = new BehaviorSubject<'and' | 'or'>('and');
    const filter = new SideBarFilter(filter$, mode$);
    const result = await firstValueFrom(filter.filter(testState));
    // Book 1 has undefined shelves (unshelved), book 2 has empty shelves array (also unshelved)
    expect(result?.books?.length).toBe(2);
  });

  it('filters by multiple readStatus values', async () => {
    const result = await firstValueFrom(filterWith({readStatus: [ReadStatus.READ, ReadStatus.UNREAD]}, 'and'));
    expect(result?.books?.length).toBe(2);
  });

  it('handles non-array filter values', async () => {
    const result = await firstValueFrom(filterWith({author: 'A1' as any}, 'and'));
    expect(result?.books?.length).toBe(0);
  });
});
