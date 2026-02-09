import { TestBed } from '@angular/core/testing';
import { SortService } from './sort.service';
import { Book } from '../model/book.model';
import { SortDirection } from '../model/sort.model';

describe('SortService', () => {
  let service: SortService;

  const createBook = (overrides: Partial<Book>): Book => ({
    id: 1,
    libraryId: 1,
    libraryName: 'Main',
    bookType: 'EPUB',
    metadata: {
      bookId: 1,
      title: 'Test',
      authors: ['Tester'],
      categories: [],
      language: 'en'
    },
    ...overrides
  } as Book);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SortService]
    });
    service = TestBed.inject(SortService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('purchaseDate sorting', () => {
    it('should sort books by purchaseDate ascending', () => {
      const books = [
        createBook({ id: 1, purchaseDate: '2024-02-01T00:00:00Z' }),
        createBook({ id: 2, purchaseDate: '2024-01-01T00:00:00Z' }),
        createBook({ id: 3, purchaseDate: '2024-03-01T00:00:00Z' })
      ];

      const sorted = service.applySort(books, {
        field: 'purchaseDate',
        label: 'Purchase Date',
        direction: SortDirection.ASCENDING
      });

      expect(sorted.map(b => b.id)).toEqual([2, 1, 3]);
    });

    it('should sort books by purchaseDate descending', () => {
      const books = [
        createBook({ id: 1, purchaseDate: '2024-02-01T00:00:00Z' }),
        createBook({ id: 2, purchaseDate: '2024-01-01T00:00:00Z' }),
        createBook({ id: 3, purchaseDate: '2024-03-01T00:00:00Z' })
      ];

      const sorted = service.applySort(books, {
        field: 'purchaseDate',
        label: 'Purchase Date',
        direction: SortDirection.DESCENDING
      });

      expect(sorted.map(b => b.id)).toEqual([3, 1, 2]);
    });

    it('should handle missing purchaseDate (treat as null/undefined)', () => {
      const books = [
        createBook({ id: 1, purchaseDate: '2024-02-01T00:00:00Z' }),
        createBook({ id: 2, purchaseDate: undefined }),
        createBook({ id: 3, purchaseDate: '2024-01-01T00:00:00Z' })
      ];

      // Ascending: nulls usually come first or last depending on implementation.
      // Looking at compareValues:
      // if (aValue == null && bValue != null) return 1; -> a (null) > b (value)
      // So nulls are "larger" than values? Wait.
      // If a is null, return 1. So a comes after b.
      // So nulls should be at the end in Ascending order?
      // Let's check logic:
      // a=null, b=date. aValue=null, bValue=time.
      // compareValues(null, time) -> returns -1 because logic says:
      // if (aValue == null && bValue != null) return 1; (WAIT line 152)
      // line 152: if (aValue == null && bValue != null) return 1;
      // This means null > value.
      // So in ascending order, nulls come last.

      const sorted = service.applySort(books, {
        field: 'purchaseDate',
        label: 'Purchase Date',
        direction: SortDirection.ASCENDING
      });

      // Expected: [3, 1, 2] (Jan, Feb, Null)
      expect(sorted.map(b => b.id)).toEqual([3, 1, 2]);
    });
  });
});
